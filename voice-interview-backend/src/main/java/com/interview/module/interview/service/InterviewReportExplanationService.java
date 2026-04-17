package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

@Service
public class InterviewReportExplanationService {

	private static final String SUMMARY_SLOT_PREFIX = "SUMMARY";
	private static final String EVIDENCE_SLOT_PREFIX = "E";
	private static final String SUGGESTION_SLOT_PREFIX = "S";

	private final AiService aiService;

	public InterviewReportExplanationService() {
		this(null);
	}

	public InterviewReportExplanationService(AiService aiService) {
		this.aiService = aiService;
	}

	public InterviewReportView enrichReport(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		if (report == null) {
			return null;
		}
		InterviewReportView ruleBackfilledReport = buildCanonicalRuleReport(report, questions, rounds);
		List<InterviewQuestionReportView> explainedQuestionReports = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(ruleBackfilledReport.questionReports())) {
			InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
			InterviewQuestionExplanationView ruleExplanation = questionReport.explanation();
			InterviewQuestionExplanationView explanation = polishQuestionExplanation(
					question,
					questionReport,
					ruleExplanation
			);
			explainedQuestionReports.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					explanation
			));
		}
		InterviewOverallExplanationView ruleOverallExplanation = ruleBackfilledReport.overallExplanation();
		return new InterviewReportView(
				ruleBackfilledReport.sessionId(),
				ruleBackfilledReport.status(),
				ruleBackfilledReport.title(),
				ruleBackfilledReport.overallScore(),
				ruleBackfilledReport.overallComment(),
				ruleBackfilledReport.strengths(),
				ruleBackfilledReport.weaknesses(),
				ruleBackfilledReport.suggestions(),
				List.copyOf(explainedQuestionReports),
				polishOverallExplanation(ruleBackfilledReport, ruleOverallExplanation),
				ruleBackfilledReport.realtimeMetrics()
		);
	}

	private InterviewReportView buildCanonicalRuleReport(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		List<InterviewQuestionReportView> questionReportsWithoutExplanations = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(report.questionReports())) {
			questionReportsWithoutExplanations.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					null
			));
		}

		InterviewReportView reportWithoutExplanations = new InterviewReportView(
				report.sessionId(),
				report.status(),
				report.title(),
				report.overallScore(),
				report.overallComment(),
				report.strengths(),
				report.weaknesses(),
				report.suggestions(),
				List.copyOf(questionReportsWithoutExplanations),
				null,
				report.realtimeMetrics()
		);
		return backfillMissingExplanations(reportWithoutExplanations, questions, rounds);
	}

	public InterviewReportView backfillMissingExplanations(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		if (report == null) {
			return null;
		}

		List<InterviewQuestionReportView> backfilledQuestionReports = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(report.questionReports())) {
			InterviewQuestionExplanationView explanation = questionReport.explanation();
			if (explanation == null) {
				InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
				explanation = buildQuestionExplanation(
						question,
						questionReport,
						roundsForQuestion(rounds, questionReport.questionIndex())
				);
			}
			backfilledQuestionReports.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					explanation
			));
		}

		InterviewOverallExplanationView overallExplanation = report.overallExplanation();
		if (overallExplanation == null) {
			overallExplanation = buildOverallExplanation(report.overallScore(), backfilledQuestionReports, rounds);
		}

		return new InterviewReportView(
				report.sessionId(),
				report.status(),
				report.title(),
				report.overallScore(),
				report.overallComment(),
				report.strengths(),
				report.weaknesses(),
				report.suggestions(),
				List.copyOf(backfilledQuestionReports),
				overallExplanation,
				report.realtimeMetrics()
		);
	}

	public InterviewOverallExplanationView buildOverallExplanation(
			Integer overallScore,
			List<InterviewQuestionReportView> questionReports,
			List<InterviewRoundRecord> rounds
	) {
		boolean noData = overallScore == null;
		String level = noData ? null : levelFromScore(overallScore);
		int strongQuestions = 0;
		int weakQuestions = 0;
		for (InterviewQuestionReportView questionReport : safeQuestionReports(questionReports)) {
			if (questionReport.score() == null) {
				continue;
			}
			if (questionReport.score() >= 80) {
				strongQuestions++;
			} else if (questionReport.score() < 60) {
				weakQuestions++;
			}
		}

		int followUpCount = 0;
		int missingPointCount = 0;
		int riskCount = 0;
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if ("FOLLOW_UP".equals(round.followUpDecision())) {
				followUpCount++;
			}
			if (!round.missingPointsSnapshot().isEmpty()) {
				missingPointCount++;
			}
			if (containsRiskSignal(round.analysisReason()) || containsRiskSignal(round.followUpDecisionReason())) {
				riskCount++;
			}
		}

		String summaryText = noData
				? "当前有效答题记录不足，尚未形成完整诊断。"
				: switch (level) {
					case "STRONG" -> "整体表现较稳定，多数题目能给出完整回答，追问下也能基本站稳结论。";
					case "MEDIUM" -> "整体基础可用，但关键点覆盖和追问深度还不够稳定，部分题目仍需要继续补强。";
					default -> "整体表现偏弱，关键点覆盖、追问深度和答题稳定性都还有明显提升空间。";
				};

		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		if (strongQuestions > 0) {
			evidencePoints.add("有 " + strongQuestions + " 道题得分达到 80 分以上，说明部分题目已经具备较完整的表达。");
		}
		if (missingPointCount > 0) {
			evidencePoints.add("共有 " + missingPointCount + " 个答题轮次暴露出关键点缺失，回答覆盖度还不够稳定。");
		}
		if (followUpCount > 0) {
			evidencePoints.add("本轮触发了 " + followUpCount + " 次继续追问，说明部分题目需要靠补充说明才能站稳。");
		}
		if (riskCount > 0) {
			evidencePoints.add("有 " + riskCount + " 个轮次出现答偏或前后不一致风险信号，需要强化题意对齐。");
		}
		if (weakQuestions > 0) {
			evidencePoints.add("有 " + weakQuestions + " 道题得分低于 60 分，短板题还需要系统复盘。");
		}
		if (evidencePoints.isEmpty()) {
			evidencePoints.add("当前有效答题记录有限，总评主要依据现有得分和分题摘要生成。");
		}

		LinkedHashSet<String> improvementSuggestions = new LinkedHashSet<>();
		if (missingPointCount > 0) {
			improvementSuggestions.add("按题型整理每题必须覆盖的关键点，先保证回答完整度。");
		}
		if (followUpCount > 0 || weakQuestions > 0) {
			improvementSuggestions.add("高频题准备“背景、方案、取舍、结果”的固定表达，提升追问稳定性。");
		}
		if (riskCount > 0) {
			improvementSuggestions.add("先对齐题干再作答，避免用相近概念替代真正被问到的核心问题。");
		}
		if (improvementSuggestions.isEmpty()) {
			improvementSuggestions.add("继续保持高频复盘，把高分题沉淀成可复用表达模板。");
		}
		if (noData) {
			evidencePoints.clear();
			evidencePoints.add("当前会话还没有足够的有效答题记录，无法稳定判断整体表现。");
			improvementSuggestions.clear();
			improvementSuggestions.add("先完成至少一题有效作答，形成评分后再查看完整诊断。");
		}

		return new InterviewOverallExplanationView(
				level,
				summaryText,
				limit(evidencePoints, 3),
				limit(improvementSuggestions, 3),
				"RULE"
		);
	}

	public InterviewQuestionExplanationView buildQuestionExplanation(
			InterviewQuestionSnapshot question,
			InterviewQuestionReportView questionReport,
			List<InterviewRoundRecord> questionRounds
	) {
		List<InterviewRoundRecord> safeQuestionRounds = safeRounds(questionRounds);
		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		int followUpCount = 0;
		InterviewRoundRecord latestAnsweredRound = null;
		InterviewRoundRecord latestEffectiveRound = null;

		for (InterviewRoundRecord round : safeQuestionRounds) {
			if ("FOLLOW_UP".equals(round.followUpDecision())) {
				followUpCount++;
			}
			if (hasAnsweredRound(round)) {
				latestAnsweredRound = round;
			}
			if (hasEffectiveAnswer(round)) {
				latestEffectiveRound = round;
			}
		}

		String questionTitle = question != null && question.titleSnapshot() != null && !question.titleSnapshot().isBlank()
				? question.titleSnapshot()
				: questionReport == null ? "当前题目" : questionReport.title();
		InterviewRoundRecord terminalRound = latestEffectiveRound == null ? latestAnsweredRound : latestEffectiveRound;
		List<String> finalMissingPoints = terminalRound == null ? List.of() : terminalRound.missingPointsSnapshot();
		boolean hasRiskSignal = terminalRound != null
				&& (containsRiskSignal(terminalRound.analysisReason()) || containsRiskSignal(terminalRound.followUpDecisionReason()));
		boolean hasDepthGapSignal = terminalRound != null
				&& (containsDepthGapSignal(terminalRound.analysisReason()) || containsDepthGapSignal(terminalRound.followUpDecisionReason()));
		boolean hasEffectiveAnswer = latestEffectiveRound != null;
		if (terminalRound != null && terminalRound.analysisReason() != null && !terminalRound.analysisReason().isBlank()) {
			evidencePoints.add(terminalRound.analysisReason().trim());
		}
		boolean noData = !hasEffectiveAnswer || questionReport == null || questionReport.score() == null;
		String performanceLevel = noData ? null : levelFromScore(questionReport.score());
		String summaryText;
		String improvementSuggestion;
		if (hasRiskSignal) {
			evidencePoints.add("分析中出现了答偏或前后不一致风险信号。");
			summaryText = questionTitle + " 这题存在答偏风险，说明回答和题目核心的对齐度还不够稳定。";
			improvementSuggestion = "先拆清题干，再按“结论、依据、方案”顺序作答，避免偏离问题本身。";
		} else if (noData) {
			evidencePoints.add(hasEffectiveAnswer
					? "当前题目已有零散记录，但还没有形成可用于判断质量的有效评分。"
					: "当前题目还没有有效作答记录，暂时无法判断回答质量。");
			summaryText = hasEffectiveAnswer
					? questionTitle + " 这题目前数据不足，尚未形成有效评分，暂时无法给出稳定判断。"
					: questionTitle + " 这题目前未作答或数据不足，尚未形成有效评分。";
			improvementSuggestion = hasEffectiveAnswer
					? "补充更完整的作答并完成当前题目，形成有效评分后再看具体问题。"
					: "先补充作答并形成有效评分，再根据结果做针对性复盘。";
		} else if (!finalMissingPoints.isEmpty()) {
			String missingPointsText = String.join("、", finalMissingPoints);
			evidencePoints.add("缺少关键点：" + missingPointsText);
			summaryText = questionTitle + " 这题还缺少对 " + missingPointsText + " 的说明，核心覆盖不够完整。";
			improvementSuggestion = "补充 " + missingPointsText + "，并明确你的方案、取舍和落地方式。";
		} else if ((hasDepthGapSignal || followUpCount >= 2)
				&& (questionReport == null || questionReport.score() == null || questionReport.score() < 80)) {
			evidencePoints.add("同一题触发了 " + followUpCount + " 次继续追问，说明细节展开还不够稳定。");
			summaryText = questionTitle + " 这题基础结论已经给出，但面对连续追问时深度和细节还不够扎实。";
			improvementSuggestion = "围绕这题补一版完整案例，重点准备过程、权衡和结果。";
		} else if (questionReport != null && questionReport.score() != null && questionReport.score() >= 80) {
			evidencePoints.add("得分较高且额外追问较少，说明回答完整度和稳定性都不错。");
			summaryText = questionTitle + " 这题回答较完整，核心点覆盖和表达稳定性都达到了较好水平。";
			improvementSuggestion = "保留现有表达结构，后续只需要继续补强案例细节。";
		} else {
			evidencePoints.add("当前回答覆盖了部分核心点，但深度和举例还可以继续补强。");
			summaryText = questionTitle + " 这题基础回答有了，但细节深度和案例支撑还不够充分。";
			improvementSuggestion = "补充更具体的案例和取舍说明，让回答从结论走到可落地方案。";
		}

		return new InterviewQuestionExplanationView(
				performanceLevel,
				summaryText,
				limit(evidencePoints, 3),
				improvementSuggestion,
				"RULE"
		);
	}

	private InterviewOverallExplanationView polishOverallExplanation(
			InterviewReportView report,
			InterviewOverallExplanationView ruleExplanation
	) {
		if (ruleExplanation == null || ruleExplanation.level() == null) {
			return ruleExplanation;
		}
		String taggedRuleSummary = tagSummary("OVERALL", ruleExplanation.level(), ruleExplanation.summaryText());
		List<String> taggedRuleEvidencePoints = tagItems(ruleExplanation.evidencePoints(), EVIDENCE_SLOT_PREFIX);
		List<String> taggedRuleSuggestions = tagItems(ruleExplanation.improvementSuggestions(), SUGGESTION_SLOT_PREFIX);
		InterviewReportExplanationResult polished = polishExplanation(new InterviewReportExplanationCommand(
				"OVERALL",
				report == null ? null : report.title(),
				report == null ? null : report.overallComment(),
				ruleExplanation.level(),
				taggedRuleSummary,
				taggedRuleEvidencePoints,
				taggedRuleSuggestions
		));
		String polishedSummary = stripTaggedSummary(
				polished == null ? null : polished.summaryText(),
				"OVERALL",
				ruleExplanation.level()
		);
		List<String> polishedEvidencePoints = stripTaggedItems(
				polished == null ? null : polished.evidencePoints(),
				taggedRuleEvidencePoints.size(),
				EVIDENCE_SLOT_PREFIX
		);
		List<String> polishedSuggestions = stripTaggedItems(
				polished == null ? null : polished.improvementSuggestions(),
				taggedRuleSuggestions.size(),
				SUGGESTION_SLOT_PREFIX
		);
		if (polishedSummary == null || polishedEvidencePoints == null || polishedSuggestions == null) {
			return ruleExplanation;
		}
		return new InterviewOverallExplanationView(
				ruleExplanation.level(),
				polishedSummary,
				polishedEvidencePoints,
				polishedSuggestions,
				"RULE_PLUS_LLM"
		);
	}

	private InterviewQuestionExplanationView polishQuestionExplanation(
			InterviewQuestionSnapshot question,
			InterviewQuestionReportView questionReport,
			InterviewQuestionExplanationView ruleExplanation
	) {
		if (ruleExplanation == null || ruleExplanation.performanceLevel() == null) {
			return ruleExplanation;
		}
		String taggedRuleSummary = tagSummary("QUESTION", ruleExplanation.performanceLevel(), ruleExplanation.summaryText());
		List<String> taggedRuleEvidencePoints = tagItems(ruleExplanation.evidencePoints(), EVIDENCE_SLOT_PREFIX);
		List<String> taggedRuleSuggestions = tagItems(suggestionAsList(ruleExplanation.improvementSuggestion()), SUGGESTION_SLOT_PREFIX);
		InterviewReportExplanationResult polished = polishExplanation(new InterviewReportExplanationCommand(
				"QUESTION",
				questionReport == null ? null : questionReport.title(),
				questionReport == null ? question == null ? null : question.promptSnapshot() : questionReport.prompt(),
				ruleExplanation.performanceLevel(),
				taggedRuleSummary,
				taggedRuleEvidencePoints,
				taggedRuleSuggestions
		));
		String polishedSummary = stripTaggedSummary(
				polished == null ? null : polished.summaryText(),
				"QUESTION",
				ruleExplanation.performanceLevel()
		);
		List<String> polishedEvidencePoints = stripTaggedItems(
				polished == null ? null : polished.evidencePoints(),
				taggedRuleEvidencePoints.size(),
				EVIDENCE_SLOT_PREFIX
		);
		List<String> polishedSuggestions = stripTaggedItems(
				polished == null ? null : polished.improvementSuggestions(),
				taggedRuleSuggestions.size(),
				SUGGESTION_SLOT_PREFIX
		);
		if (polishedSummary == null
				|| polishedEvidencePoints == null
				|| polishedSuggestions == null
				|| polishedSuggestions.size() != 1) {
			return ruleExplanation;
		}
		return new InterviewQuestionExplanationView(
				ruleExplanation.performanceLevel(),
				polishedSummary,
				polishedEvidencePoints,
				polishedSuggestions.get(0),
				"RULE_PLUS_LLM"
		);
	}

	private InterviewReportExplanationResult polishExplanation(InterviewReportExplanationCommand command) {
		if (aiService == null || command == null) {
			return null;
		}
		try {
			return aiService.polishInterviewReportExplanation(command);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private InterviewQuestionSnapshot findQuestion(List<InterviewQuestionSnapshot> questions, int questionIndex) {
		for (InterviewQuestionSnapshot question : questions == null ? List.<InterviewQuestionSnapshot>of() : questions) {
			if (question.questionIndex() == questionIndex) {
				return question;
			}
		}
		return null;
	}

	private List<InterviewRoundRecord> roundsForQuestion(List<InterviewRoundRecord> rounds, int questionIndex) {
		List<InterviewRoundRecord> questionRounds = new ArrayList<>();
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if (round.questionIndex() == questionIndex) {
				questionRounds.add(round);
			}
		}
		return questionRounds;
	}

	private List<InterviewRoundRecord> safeRounds(List<InterviewRoundRecord> rounds) {
		return rounds == null ? List.of() : rounds;
	}

	private List<InterviewQuestionReportView> safeQuestionReports(List<InterviewQuestionReportView> questionReports) {
		return questionReports == null ? List.of() : questionReports;
	}

	private List<String> suggestionAsList(String suggestion) {
		if (suggestion == null || suggestion.isBlank()) {
			return List.of();
		}
		return List.of(suggestion.trim());
	}

	private List<String> normalizeItems(List<String> items) {
		List<String> normalized = new ArrayList<>();
		for (String item : items == null ? List.<String>of() : items) {
			if (item == null || item.isBlank()) {
				continue;
			}
			normalized.add(item.trim());
		}
		return List.copyOf(normalized);
	}

	private String tagSummary(String scope, String level, String summaryText) {
		if (summaryText == null || summaryText.isBlank()) {
			return null;
		}
		return summarySlotTag(scope, level) + " " + summaryText.trim();
	}

	private String stripTaggedSummary(String summaryText, String scope, String level) {
		if (summaryText == null || summaryText.isBlank()) {
			return null;
		}
		String normalized = summaryText.trim();
		String tag = summarySlotTag(scope, level);
		if (!normalized.startsWith(tag)) {
			return null;
		}
		String content = normalized.substring(tag.length()).trim();
		if (content.isBlank()) {
			return null;
		}
		return content;
	}

	private List<String> tagItems(List<String> items, String slotPrefix) {
		List<String> normalized = normalizeItems(items);
		List<String> tagged = new ArrayList<>();
		for (int index = 0; index < normalized.size(); index++) {
			tagged.add(slotTag(slotPrefix, index + 1) + " " + normalized.get(index));
		}
		return List.copyOf(tagged);
	}

	private List<String> stripTaggedItems(List<String> items, int expectedCount, String slotPrefix) {
		if (items == null || items.size() != expectedCount) {
			return null;
		}
		List<String> stripped = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			String item = items.get(index);
			if (item == null || item.isBlank()) {
				return null;
			}
			String normalized = item.trim();
			String slotTag = slotTag(slotPrefix, index + 1);
			if (!normalized.startsWith(slotTag)) {
				return null;
			}
			String content = normalized.substring(slotTag.length()).trim();
			if (content.isBlank()) {
				return null;
			}
			stripped.add(content);
		}
		return List.copyOf(stripped);
	}

	private String slotTag(String slotPrefix, int slotIndex) {
		return "[" + slotPrefix + slotIndex + "]";
	}

	private String summarySlotTag(String scope, String level) {
		return "[" + SUMMARY_SLOT_PREFIX + ":" + scope + ":" + level + "]";
	}

	private String levelFromScore(Integer score) {
		if (score != null && score >= 80) {
			return "STRONG";
		}
		if (score != null && score < 60) {
			return "WEAK";
		}
		return "MEDIUM";
	}

	private boolean containsRiskSignal(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return text.contains("不一致")
				|| text.contains("答偏风险")
				|| text.contains("偏题")
				|| text.contains("偏离题目")
				|| text.contains("回到题目本身")
				|| text.contains("不太一致")
				|| text.contains("风险");
	}

	private boolean containsDepthGapSignal(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return text.contains("细节不足")
				|| text.contains("过程不足")
				|| text.contains("缺少案例")
				|| text.contains("深度不足")
				|| text.contains("结论化")
				|| text.contains("案例支撑")
				|| text.contains("补充实际处理过程")
				|| text.contains("展开不足");
	}

	private boolean hasEffectiveAnswer(InterviewRoundRecord round) {
		if (round == null || round.userAnswerText() == null || round.userAnswerText().isBlank()) {
			return false;
		}
		return !"候选人未提供有效回答。".equals(round.userAnswerText().trim());
	}

	private boolean hasAnsweredRound(InterviewRoundRecord round) {
		return round != null && round.userAnswerText() != null;
	}

	private List<String> limit(LinkedHashSet<String> items, int maxSize) {
		List<String> limited = new ArrayList<>();
		for (String item : items) {
			if (item == null || item.isBlank()) {
				continue;
			}
			limited.add(item);
			if (limited.size() >= maxSize) {
				break;
			}
		}
		return List.copyOf(limited);
	}
}
