package com.interview.module.interview.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.interview.engine.model.InterviewMessageView;
import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewRoundRecord;
import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.model.InterviewSessionSummaryView;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.engine.store.InterviewReportStore;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;
import com.interview.module.interview.engine.store.NoopInterviewReportStore;
import com.interview.module.interview.service.AnswerEvidence;
import com.interview.module.interview.service.InterviewAnswerAnalyzer;
import com.interview.module.tts.service.TtsAudioResult;
import com.interview.module.tts.service.TtsRenderOptions;
import com.interview.module.tts.service.TtsService;

@Service
public class SimpleInterviewEngine implements InterviewEngine {

	private final InterviewSessionStore sessionStore;
	private final InterviewReportStore interviewReportStore;
	private final AiService aiService;
	private final TtsService ttsService;

	public SimpleInterviewEngine(
			InterviewSessionStore sessionStore,
			ObjectProvider<InterviewReportStore> interviewReportStoreProvider,
			AiService aiService,
			TtsService ttsService
	) {
		this.sessionStore = sessionStore;
		this.interviewReportStore = interviewReportStoreProvider.getIfAvailable(NoopInterviewReportStore::new);
		this.aiService = aiService;
		this.ttsService = ttsService;
	}

	@Override
	public InterviewSessionView startSession(
			List<InterviewQuestionCard> questions,
			int durationMinutes,
			int maxFollowUpPerQuestion,
			InterviewSessionOwner owner,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
		List<InterviewQuestionCard> effectiveQuestions = questions == null || questions.isEmpty()
				? List.of(new InterviewQuestionCard("自我介绍", "请先做一个简短的自我介绍。"))
				: questions;
		List<InterviewQuestionSnapshot> questionSnapshots = new ArrayList<>();
		for (int index = 0; index < effectiveQuestions.size(); index++) {
			InterviewQuestionCard question = effectiveQuestions.get(index);
			questionSnapshots.add(new InterviewQuestionSnapshot(
					index + 1,
					question.title(),
					question.prompt(),
					normalizeQuestionSource(question.sourceType()),
					normalizeQuestionDifficulty(question.difficulty())
			));
		}
		InterviewSessionState sessionState = new InterviewSessionState(
				"demo-" + UUID.randomUUID().toString().replace("-", ""),
				owner.userId(),
				owner.nickname(),
				questionSnapshots,
				InterviewStage.OPENING.name(),
				durationMinutes,
				maxFollowUpPerQuestion,
				interviewerSpeakerId,
				interviewerSpeechSpeed
		);
		appendAssistantRound(sessionState, questionSnapshots.get(0).promptSnapshot(), "QUESTION", null);
		sessionStore.save(sessionState);
		return toView(sessionState);
	}

	@Override
	public InterviewSessionView getState(String sessionId, String requesterUserId) {
		return toView(requireSession(sessionId, requesterUserId));
	}

	@Override
	public InterviewSessionView answer(
			String sessionId,
			String requesterUserId,
			String answerMode,
			String userText,
			String userAudioUrl
	) {
		InterviewSessionState sessionState = requireSession(sessionId, requesterUserId);
		synchronized (sessionState) {
			requireActive(sessionState);
			String normalizedText = normalize(userText);
			InterviewQuestionSnapshot currentQuestion = currentQuestion(sessionState);
			List<String> expectedPoints = expectedPoints(currentQuestion);
			AnswerEvidence analysis = aiService.analyzeInterviewAnswer(
					currentQuestion.promptSnapshot(),
					normalizedText,
					expectedPoints
			);
			appendUserAnswer(sessionState, normalizedText, userAudioUrl, answerMode, analysis.reason());

			AiReply aiReply = aiService.generateInterviewReply(new InterviewReplyCommand(
					currentQuestion.promptSnapshot(),
					normalizedText,
					sessionState.getStage(),
					sessionState.getFollowUpIndex(),
					sessionState.getMaxFollowUpPerQuestion(),
					expectedPoints
			));
			if (shouldFollowUp(sessionState, aiReply, analysis)) {
				sessionState.setFollowUpIndex(sessionState.getFollowUpIndex() + 1);
				appendAssistantRound(
						sessionState,
						buildFollowUpPrompt(aiReply, analysis),
						"FOLLOW_UP",
						aiReply.scoreSuggestion()
				);
				sessionStore.save(sessionState);
				return toView(sessionState);
			}

			if (hasNextQuestion(sessionState)) {
				sessionState.setCurrentQuestionIndex(sessionState.getCurrentQuestionIndex() + 1);
				sessionState.setFollowUpIndex(0);
				sessionState.setStage(resolveStage(sessionState));
				InterviewQuestionSnapshot nextQuestion = currentQuestion(sessionState);
				appendAssistantRound(sessionState, nextQuestion.promptSnapshot(), "QUESTION", aiReply.scoreSuggestion());
				sessionStore.save(sessionState);
				return toView(sessionState);
			}

			sessionState.setStatus("COMPLETED");
			sessionState.setStage(InterviewStage.WRAP_UP.name());
			appendAssistantRound(
					sessionState,
					buildClosingText(aiReply.scoreSuggestion()),
					"END_INTERVIEW",
					aiReply.scoreSuggestion()
			);
			sessionStore.save(sessionState);
			persistReport(sessionState);
			return toView(sessionState);
		}
	}

	@Override
	public InterviewSessionView skip(String sessionId, String requesterUserId) {
		InterviewSessionState sessionState = requireSession(sessionId, requesterUserId);
		synchronized (sessionState) {
			requireActive(sessionState);
			appendUserAnswer(sessionState, "本题已跳过。", null, "SKIP", "候选人主动跳过当前问题");

			if (hasNextQuestion(sessionState)) {
				sessionState.setCurrentQuestionIndex(sessionState.getCurrentQuestionIndex() + 1);
				sessionState.setFollowUpIndex(0);
				sessionState.setStage(resolveStage(sessionState));
				InterviewQuestionSnapshot nextQuestion = currentQuestion(sessionState);
				appendAssistantRound(sessionState, "收到，我们跳过这一题。下一题：" + nextQuestion.promptSnapshot(), "QUESTION", null);
				sessionStore.save(sessionState);
				return toView(sessionState);
			}

			sessionState.setStatus("COMPLETED");
			sessionState.setStage(InterviewStage.WRAP_UP.name());
			appendAssistantRound(sessionState, "最后一题已跳过。本轮 demo 面试结束。", "END_INTERVIEW", null);
			sessionStore.save(sessionState);
			persistReport(sessionState);
			return toView(sessionState);
		}
	}

	@Override
	public InterviewSessionView end(String sessionId, String requesterUserId) {
		InterviewSessionState sessionState = requireSession(sessionId, requesterUserId);
		synchronized (sessionState) {
			if (!"IN_PROGRESS".equals(sessionState.getStatus())) {
				return toView(sessionState);
			}
			sessionState.setStatus("CANCELLED");
			sessionState.setStage(InterviewStage.WRAP_UP.name());
			appendAssistantRound(sessionState, "收到，本轮 demo 面试到此结束。你可以稍后查看历史和报告。", "END_INTERVIEW", null);
			sessionStore.save(sessionState);
			persistReport(sessionState);
			return toView(sessionState);
		}
	}

	@Override
	public List<InterviewSessionSummaryView> listSessions(String requesterUserId) {
		return sessionStore.findAll().stream()
				.filter(session -> session.getOwnerUserId().equals(requesterUserId))
				.map(this::toSummaryView)
				.sorted(Comparator.comparing(InterviewSessionSummaryView::lastUpdatedAt, Comparator.nullsLast(String::compareTo)).reversed())
				.toList();
	}

	@Override
	public InterviewReportView getReport(String sessionId, String requesterUserId) {
		InterviewSessionState sessionState = requireSession(sessionId, requesterUserId);
		if ("IN_PROGRESS".equals(sessionState.getStatus())) {
			return toReportView(sessionState);
		}
		return interviewReportStore.findBySessionId(sessionId)
				.orElseGet(() -> persistReport(sessionState));
	}

	private InterviewReportView persistReport(InterviewSessionState sessionState) {
		InterviewReportView report = toReportView(sessionState);
		interviewReportStore.save(report);
		return report;
	}

	private InterviewSessionState requireSession(String sessionId, String requesterUserId) {
		InterviewSessionState sessionState = sessionStore.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("Interview session not found: " + sessionId));
		if (!sessionState.getOwnerUserId().equals(requesterUserId)) {
			throw new IllegalArgumentException("Interview session does not belong to current user");
		}
		return sessionState;
	}

	private void requireActive(InterviewSessionState sessionState) {
		if (!"IN_PROGRESS".equals(sessionState.getStatus())) {
			throw new IllegalStateException("Interview session is not active: " + sessionState.getStatus());
		}
	}

	private boolean hasNextQuestion(InterviewSessionState sessionState) {
		return sessionState.getCurrentQuestionIndex() < sessionState.getQuestions().size() - 1;
	}

	private InterviewQuestionSnapshot currentQuestion(InterviewSessionState sessionState) {
		return sessionState.getQuestions().get(sessionState.getCurrentQuestionIndex());
	}

	private boolean shouldFollowUp(
			InterviewSessionState sessionState,
			AiReply aiReply,
			AnswerEvidence analysis
	) {
		return (analysis.followUpNeeded() || "FOLLOW_UP".equalsIgnoreCase(aiReply.decisionSuggestion()))
				&& sessionState.getFollowUpIndex() < sessionState.getMaxFollowUpPerQuestion();
	}

	private void appendUserAnswer(
			InterviewSessionState sessionState,
			String text,
			String userAudioUrl,
			String answerMode,
			String analysisReason
	) {
		int latestRoundIndex = sessionState.getRounds().size() - 1;
		if (latestRoundIndex < 0) {
			throw new IllegalStateException("No round exists to attach the user answer");
		}
		InterviewRoundRecord latestRound = sessionState.getRounds().get(latestRoundIndex);
		sessionState.getRounds().set(
				latestRoundIndex,
				latestRound.withUserAnswer(text, userAudioUrl, answerMode, Instant.now().toString(), analysisReason)
		);
	}

	private void appendAssistantRound(InterviewSessionState sessionState, String text, String roundType, Integer scoreSuggestion) {
		TtsAudioResult ttsAudio = ttsService.synthesize(
				text,
				new TtsRenderOptions(sessionState.getInterviewerSpeakerId(), sessionState.getInterviewerSpeechSpeed())
		);
		sessionState.getRounds().add(new InterviewRoundRecord(
				UUID.randomUUID().toString(),
				sessionState.getCurrentQuestionIndex() + 1,
				sessionState.getFollowUpIndex(),
				roundType,
				text,
				ttsAudio.audioUrl(),
				ttsAudio.durationMs(),
				scoreSuggestion,
				null,
				null,
				null,
				Instant.now().toString(),
				null,
				null
		));
	}

	private InterviewSessionView toView(InterviewSessionState sessionState) {
		InterviewQuestionSnapshot currentQuestion = sessionState.getCurrentQuestionIndex() < sessionState.getQuestions().size()
				? sessionState.getQuestions().get(sessionState.getCurrentQuestionIndex())
				: null;
		List<InterviewRoundRecord> rounds = List.copyOf(sessionState.getRounds());
		List<InterviewMessageView> messages = new ArrayList<>();
		for (InterviewRoundRecord round : rounds) {
			messages.add(new InterviewMessageView(
					round.roundId(),
					"ai",
					"AI 面试官",
					round.aiMessageText(),
					round.roundType(),
					round.questionIndex(),
					round.followUpIndex(),
					round.aiAudioUrl(),
					round.aiAudioDurationMs(),
					round.scoreSuggestion(),
					null,
					round.createdAt()
			));
			if (round.userAnswerText() != null) {
				messages.add(new InterviewMessageView(
						round.roundId() + "-user",
						"user",
						"我",
						round.userAnswerText(),
						"ANSWER",
						round.questionIndex(),
						round.followUpIndex(),
						round.userAudioUrl(),
						0L,
						null,
						round.userAnswerMode(),
						round.answeredAt()
				));
			}
		}
		return new InterviewSessionView(
				sessionState.getSessionId(),
				sessionState.getStatus(),
				sessionState.getStage(),
				sessionState.getDurationMinutes(),
				sessionState.getCurrentQuestionIndex() + 1,
				sessionState.getQuestions().size(),
				sessionState.getFollowUpIndex(),
				sessionState.getMaxFollowUpPerQuestion(),
				currentQuestion == null ? null : currentQuestion.titleSnapshot(),
				currentQuestion == null ? null : currentQuestion.promptSnapshot(),
				List.copyOf(sessionState.getQuestions()),
				rounds,
				messages
		);
	}

	private InterviewSessionSummaryView toSummaryView(InterviewSessionState sessionState) {
		List<InterviewRoundRecord> rounds = sessionState.getRounds();
		String startedAt = rounds.isEmpty() ? null : rounds.get(0).createdAt();
		String lastUpdatedAt = rounds.isEmpty()
				? null
				: rounds.get(rounds.size() - 1).answeredAt() != null
					? rounds.get(rounds.size() - 1).answeredAt()
					: rounds.get(rounds.size() - 1).createdAt();
		int answeredRounds = (int) rounds.stream().filter(round -> round.userAnswerText() != null).count();
		Integer overallScore = computeOverallScore(rounds);
		String title = sessionState.getQuestions().isEmpty() ? "模拟面试" : sessionState.getQuestions().get(0).titleSnapshot();
		String summary = overallScore == null
				? "会话已创建，等待更多答题记录。"
				: overallScore >= 80
					? "表现稳定，适合继续做更深的追问训练。"
					: overallScore >= 60
						? "基础可用，但关键场景和取舍还需要强化。"
						: "建议继续练核心概念、表达结构和追问深度。";
		return new InterviewSessionSummaryView(
				sessionState.getSessionId(),
				sessionState.getStatus(),
				title,
				startedAt,
				lastUpdatedAt,
				sessionState.getQuestions().size(),
				answeredRounds,
				overallScore,
				summary,
				sessionState.getStage(),
				sessionState.getDurationMinutes()
		);
	}

	private InterviewReportView toReportView(InterviewSessionState sessionState) {
		Map<Integer, InterviewRoundRecord> latestRoundByQuestion = new LinkedHashMap<>();
		for (InterviewRoundRecord round : sessionState.getRounds()) {
			latestRoundByQuestion.put(round.questionIndex(), round);
		}

		List<InterviewQuestionReportView> questionReports = new ArrayList<>();
		for (InterviewQuestionSnapshot question : sessionState.getQuestions()) {
			InterviewRoundRecord round = latestRoundByQuestion.get(question.questionIndex());
			Integer score = round == null ? null : round.scoreSuggestion();
			String summary = score == null
					? "当前题目还没有形成有效评分。"
					: score >= 80
						? "回答较完整，追问表现也比较稳定。"
						: score >= 60
							? "核心点回答到了，但细节与例子还可以更深入。"
							: "回答覆盖度不足，建议回到基础概念和经典场景重新练。";
			questionReports.add(new InterviewQuestionReportView(
					question.questionIndex(),
					question.titleSnapshot(),
					question.promptSnapshot(),
					score,
					summary
			));
		}

		Integer overallScore = computeOverallScore(sessionState.getRounds());
		String overallComment = overallScore == null
				? "当前会话还没有足够的有效答题记录来生成完整评分。"
				: overallScore >= 80
					? "整体表现不错，已经具备较好的工程表达和追问应对能力。"
					: overallScore >= 60
						? "整体基础可用，但关键场景的深度和取舍表达还需要继续补强。"
						: "整体表现偏弱，建议先回到核心概念、场景题和表达结构进行系统复盘。";

		List<String> strengths = new ArrayList<>();
		List<String> weaknesses = new ArrayList<>();
		List<String> suggestions = new ArrayList<>();

		if (overallScore == null || overallScore < 80) {
			weaknesses.add("追问深度还不稳定，遇到进一步追问时细节展开不足。");
			suggestions.add("每个主题准备 1-2 个真实项目例子，回答时带上背景、决策和结果。");
		} else {
			strengths.add("面对追问时能够保持基本结构，说明工程表达有一定基础。");
		}

		if (overallScore == null || overallScore < 70) {
			weaknesses.add("关键场景题的原理和取舍表达还不够系统。");
			suggestions.add("围绕高并发、事务一致性、缓存、消息队列四类题做专题复盘。");
		} else {
			strengths.add("对常见后端场景具备一定工程语境，不只是停留在概念层。");
		}

		if (strengths.isEmpty()) {
			strengths.add("已经开始具备完整答题意识，后续重点是把深度和案例补齐。");
		}

		if (weaknesses.isEmpty()) {
			weaknesses.add("当前报告未发现明显短板，但仍建议继续强化追问和取舍表达。");
		}

		if (suggestions.isEmpty()) {
			suggestions.add("保持一周 2-3 次高频练习，重点复盘被追问最多的题。");
		}

		String title = sessionState.getQuestions().isEmpty() ? "模拟面试报告" : sessionState.getQuestions().get(0).titleSnapshot();
		return new InterviewReportView(
				sessionState.getSessionId(),
				sessionState.getStatus(),
				title,
				overallScore,
				overallComment,
				List.copyOf(strengths),
				List.copyOf(weaknesses),
				List.copyOf(suggestions),
				List.copyOf(questionReports)
		);
	}

	private Integer computeOverallScore(List<InterviewRoundRecord> rounds) {
		List<Integer> scores = rounds.stream()
				.map(InterviewRoundRecord::scoreSuggestion)
				.filter(score -> score != null)
				.toList();
		if (scores.isEmpty()) {
			return null;
		}
		return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
	}

	private String normalize(String text) {
		if (text == null || text.isBlank()) {
			return "候选人未提供有效回答。";
		}
		return text.trim();
	}

	private String normalizeQuestionSource(String sourceType) {
		if (sourceType == null || sourceType.isBlank()) {
			return "PRESET";
		}
		return sourceType;
	}

	private Integer normalizeQuestionDifficulty(Integer difficulty) {
		return difficulty == null ? 1 : difficulty;
	}

	private String buildClosingText(Integer scoreSuggestion) {
		if (scoreSuggestion == null) {
			return "本轮 demo 面试结束。你可以继续补充准备薄弱项后再来一轮。";
		}
		if (scoreSuggestion >= 80) {
			return "本轮 demo 面试结束。整体表现不错，建议继续巩固细节深度。";
		}
		if (scoreSuggestion >= 60) {
			return "本轮 demo 面试结束。基础还可以，建议重点补齐关键场景的原理和取舍。";
		}
		return "本轮 demo 面试结束。建议从核心概念、常见场景和追问深度三方面继续补强。";
	}

	private List<String> expectedPoints(InterviewQuestionSnapshot question) {
		if (question == null) {
			return List.of();
		}
		List<String> points = new ArrayList<>();
		if (question.titleSnapshot() != null
				&& !question.titleSnapshot().isBlank()
				&& !"自我介绍".equals(question.titleSnapshot().trim())) {
			points.add(question.titleSnapshot());
		}
		if (question.promptSnapshot() != null && (question.promptSnapshot().contains("和") || question.promptSnapshot().contains("、"))) {
			String normalizedPrompt = question.promptSnapshot().replace("、", "和");
			for (String segment : normalizedPrompt.split("和")) {
				String normalized = segment.replace("请说明", "").replace("请介绍", "").trim();
				if (!normalized.isBlank() && normalized.length() <= 12) {
					points.add(normalized);
				}
			}
		}
		return points.stream().distinct().toList();
	}

	private String buildFollowUpPrompt(AiReply aiReply, AnswerEvidence analysis) {
		if (analysis.followUpNeeded() && !analysis.missingPoints().isEmpty()) {
			return "你刚才的回答还缺少这些点：" + String.join("、", analysis.missingPoints()) + "。请补充说明。";
		}
		return aiReply.spokenText();
	}

	private String resolveStage(InterviewSessionState sessionState) {
		int totalQuestions = sessionState.getQuestions().size();
		int currentIndex = sessionState.getCurrentQuestionIndex();
		if (currentIndex <= 0) {
			return InterviewStage.OPENING.name();
		}
		if (currentIndex >= Math.max(1, totalQuestions - 1)) {
			return InterviewStage.WRAP_UP.name();
		}
		if (currentIndex <= Math.max(1, totalQuestions / 2)) {
			return InterviewStage.JAVA_CORE.name();
		}
		return InterviewStage.PROJECT_DEEP_DIVE.name();
	}
}
