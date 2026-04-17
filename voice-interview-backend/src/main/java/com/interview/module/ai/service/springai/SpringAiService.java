package com.interview.module.ai.service.springai;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReplyOutput;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.ai.service.ResumeKeywordOutput;
import com.interview.module.ai.service.ResumeQuestionListOutput;
import com.interview.module.ai.service.ResumeQuestionOutput;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "springai", matchIfMissing = true)
public class SpringAiService implements AiService {

	private static final String PROVIDER = "springai";
	private static final String DEFAULT_REPLY_TEXT = "好的，我们继续。";
	private static final String DEFAULT_DECISION = "FOLLOW_UP";
	private static final String DEFAULT_RESUME_SUMMARY = "候选人具备技术开发经验";
	private static final String DEFAULT_QUESTION_TITLE = "面试题";
	private static final String DEFAULT_QUESTION_PROMPT = "请介绍一下你的相关经验";
	private static final int DEFAULT_DIFFICULTY = 2;

	private final ChatClient chatClient;
	private final ProviderMetricsService providerMetricsService;

	public SpringAiService(ChatClient chatClient, ProviderMetricsService providerMetricsService) {
		this.chatClient = chatClient;
		this.providerMetricsService = providerMetricsService;
	}

	@Override
	public AiReply generateInterviewReply(InterviewReplyCommand command) {
		return providerMetricsService.record("AI", PROVIDER, () -> {
			InterviewReplyOutput output = chatClient.prompt()
					.system("You are a technical interviewer.\nReturn JSON matching InterviewReplyOutput only.")
					.user(u -> u.text("""
							question: {question}
							answer: {answer}
							stage: {stage}
							followUpIndex: {followUpIndex}
							maxFollowUpPerQuestion: {maxFollowUpPerQuestion}
							expectedPoints: {expectedPoints}
							""")
							.param("question", command == null ? "" : command.question())
							.param("answer", command == null ? "" : command.answer())
							.param("stage", command == null ? "" : command.stage())
							.param("followUpIndex", String.valueOf(command == null ? 0 : command.followUpIndex()))
							.param("maxFollowUpPerQuestion", String.valueOf(command == null ? 0 : command.maxFollowUpPerQuestion()))
							.param("expectedPoints", command == null ? List.of() : command.expectedPoints()))
					.call()
					.entity(InterviewReplyOutput.class);

			if (output == null) {
				return new AiReply(DEFAULT_REPLY_TEXT, DEFAULT_DECISION, null);
			}
			return new AiReply(
					firstNonBlank(output.spokenText(), DEFAULT_REPLY_TEXT),
					firstNonBlank(output.decisionSuggestion(), DEFAULT_DECISION),
					output.scoreSuggestion()
			);
		});
	}

	@Override
	public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
		return providerMetricsService.record("AI_RESUME_KEYWORDS", PROVIDER, () -> {
			ResumeKeywordOutput output = chatClient.prompt()
					.system("你是技术简历分析助手。\n只返回符合 ResumeKeywordOutput 的 JSON。")
					.user(u -> u.text("resumeText:\n{resumeText}")
							.param("resumeText", resumeText == null ? "" : resumeText))
					.call()
					.entity(ResumeKeywordOutput.class);

			if (output == null) {
				return new ResumeKeywordExtractionResult(DEFAULT_RESUME_SUMMARY, List.of(), List.of());
			}
			return new ResumeKeywordExtractionResult(
					firstNonBlank(output.summary(), DEFAULT_RESUME_SUMMARY),
					normalizeList(output.keywords()),
					normalizeList(output.experienceHighlights())
			);
		});
	}

	@Override
	public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
		return providerMetricsService.record("AI_RESUME_QUESTIONS", PROVIDER, () -> {
			if (command == null || command.questionCount() <= 0) {
				return List.of();
			}
			ResumeQuestionListOutput output = chatClient.prompt()
					.system("你是技术面试出题助手。\n只返回符合 ResumeQuestionListOutput 的 JSON。")
					.user(u -> u.text("""
							resumeSummary: {resumeSummary}
							keywords: {keywords}
							existingQuestionTitles: {existingQuestionTitles}
							missingKeywords: {missingKeywords}
							questionCount: {questionCount}
							""")
							.param("resumeSummary", command.resumeSummary())
							.param("keywords", command.keywords())
							.param("existingQuestionTitles", command.existingQuestionTitles())
							.param("missingKeywords", command.missingKeywords())
							.param("questionCount", String.valueOf(command.questionCount())))
					.call()
					.entity(ResumeQuestionListOutput.class);

			if (output == null || output.questions() == null) {
				return List.of();
			}
			return output.questions().stream()
					.limit(command.questionCount())
					.map(this::toGeneratedQuestion)
					.toList();
		});
	}

	@Override
	public InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
		return providerMetricsService.record("AI_REPORT_EXPLANATION", PROVIDER, () -> {
			if (command == null) {
				return null;
			}
			InterviewReportExplanationResult output = chatClient.prompt()
					.system("""
							你是面试报告解释润色助手。
							summaryText、evidencePoints 和 improvementSuggestions 都会带稳定槽位标记，例如 [SUMMARY:OVERALL:MEDIUM]、[SUMMARY:QUESTION:WEAK]、[E1]、[S1]。
							只允许润色槽位标记后面的文案，不允许改写原有结论、证据事实、建议方向、强弱判断，也不允许改动 summaryText 的槽位标记、列表槽位标记、列表顺序或数量。
							保持 summaryText、evidencePoints 和 improvementSuggestions 的语义不变，仅优化措辞。
							如果无法严格保留每个槽位标记及其顺序，就原样返回对应项，不要自行重排。
							只返回符合 InterviewReportExplanationResult 的 JSON。
							""")
					.user(u -> u.text("""
							scope: {scope}
							title: {title}
							prompt: {prompt}
							level: {level}
							summaryText: {summaryText}
							evidencePoints: {evidencePoints}
							improvementSuggestions: {improvementSuggestions}
							""")
							.param("scope", defaultString(command.scope()))
							.param("title", defaultString(command.title()))
							.param("prompt", defaultString(command.prompt()))
							.param("level", defaultString(command.level()))
							.param("summaryText", defaultString(command.summaryText()))
							.param("evidencePoints", command.evidencePoints())
							.param("improvementSuggestions", command.improvementSuggestions()))
					.call()
					.entity(InterviewReportExplanationResult.class);

			if (output == null) {
				throw new IllegalStateException("Failed to parse report explanation polish result as JSON");
			}
			InterviewReportExplanationResult normalizedOutput = new InterviewReportExplanationResult(
					blankToNull(output.summaryText()),
					normalizeList(output.evidencePoints()),
					normalizeList(output.improvementSuggestions())
			);
			return validateReportExplanationContract(command, normalizedOutput);
		});
	}

	private GeneratedResumeQuestion toGeneratedQuestion(ResumeQuestionOutput output) {
		if (output == null) {
			return new GeneratedResumeQuestion(DEFAULT_QUESTION_TITLE, DEFAULT_QUESTION_PROMPT, null, DEFAULT_DIFFICULTY);
		}
		return new GeneratedResumeQuestion(
				firstNonBlank(output.title(), DEFAULT_QUESTION_TITLE),
				firstNonBlank(output.prompt(), DEFAULT_QUESTION_PROMPT),
				blankToNull(output.targetKeyword()),
				output.difficulty() == null ? DEFAULT_DIFFICULTY : output.difficulty()
		);
	}

	private List<String> normalizeList(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.toList();
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String defaultString(String value) {
		return value == null ? "" : value;
	}

	private String firstNonBlank(String value, String fallback) {
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		return fallback;
	}

	private InterviewReportExplanationResult validateReportExplanationContract(
			InterviewReportExplanationCommand command,
			InterviewReportExplanationResult result
	) {
		if (result.summaryText() == null || result.summaryText().isBlank()) {
			throw new IllegalStateException("Invalid report explanation contract: summaryText is missing");
		}
		String expectedSummaryTag = extractLeadingTag(command.summaryText());
		if (expectedSummaryTag == null || !result.summaryText().trim().startsWith(expectedSummaryTag)) {
			throw new IllegalStateException("Invalid report explanation contract: summaryText slot marker is missing");
		}
		return result;
	}

	private String extractLeadingTag(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		if (!normalized.startsWith("[")) {
			return null;
		}
		int tagEnd = normalized.indexOf(']');
		if (tagEnd <= 1) {
			return null;
		}
		return normalized.substring(0, tagEnd + 1);
	}
}
