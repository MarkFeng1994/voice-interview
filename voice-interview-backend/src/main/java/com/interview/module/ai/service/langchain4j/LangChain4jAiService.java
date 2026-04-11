package com.interview.module.ai.service.langchain4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "langchain4j")
public class LangChain4jAiService implements AiService {

	private static final String PROVIDER = "langchain4j";
	private static final String DEFAULT_REPLY_TEXT = "好的，我们继续。";
	private static final String DEFAULT_DECISION = "FOLLOW_UP";
	private static final String DEFAULT_RESUME_SUMMARY = "候选人具备技术开发经验";
	private static final String DEFAULT_QUESTION_TITLE = "面试题";
	private static final String DEFAULT_QUESTION_PROMPT = "请介绍一下你的相关经验";
	private static final int DEFAULT_DIFFICULTY = 2;

	private final InterviewReplyAssistant interviewReplyAssistant;
	private final ResumeKeywordAssistant resumeKeywordAssistant;
	private final ResumeQuestionAssistant resumeQuestionAssistant;
	private final InterviewReportExplanationAssistant interviewReportExplanationAssistant;
	private final ProviderMetricsService providerMetricsService;
	private final ObjectMapper objectMapper;

	public LangChain4jAiService(
			LangChain4jAssistantFactory assistantFactory,
			ProviderMetricsService providerMetricsService,
			ObjectMapper objectMapper
	) {
		this.interviewReplyAssistant = assistantFactory.interviewReplyAssistant();
		this.resumeKeywordAssistant = assistantFactory.resumeKeywordAssistant();
		this.resumeQuestionAssistant = assistantFactory.resumeQuestionAssistant();
		this.interviewReportExplanationAssistant = assistantFactory.interviewReportExplanationAssistant();
		this.providerMetricsService = providerMetricsService;
		this.objectMapper = objectMapper;
	}

	@Override
	public AiReply generateInterviewReply(InterviewReplyCommand command) {
		return providerMetricsService.record("AI", PROVIDER, () -> {
			String content = interviewReplyAssistant.generate(
					command == null ? "" : command.question(),
					command == null ? "" : command.answer(),
					command == null ? "" : command.stage(),
					command == null ? 0 : command.followUpIndex(),
					command == null ? 0 : command.maxFollowUpPerQuestion(),
					command == null ? List.of() : command.expectedPoints()
			);
			InterviewReplyOutput output = parseJsonObject(content, InterviewReplyOutput.class);
			return new AiReply(
					firstNonBlank(output == null ? null : output.spokenText(), DEFAULT_REPLY_TEXT),
					firstNonBlank(output == null ? null : output.decisionSuggestion(), DEFAULT_DECISION),
					output == null ? null : output.scoreSuggestion()
			);
		});
	}

	@Override
	public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
		return providerMetricsService.record("AI_RESUME_KEYWORDS", PROVIDER, () -> {
			String content = resumeKeywordAssistant.extract(resumeText == null ? "" : resumeText);
			ResumeKeywordOutput output = parseJsonObject(content, ResumeKeywordOutput.class);
			return new ResumeKeywordExtractionResult(
					firstNonBlank(output == null ? null : output.summary(), DEFAULT_RESUME_SUMMARY),
					normalizeList(output == null ? null : output.keywords()),
					normalizeList(output == null ? null : output.experienceHighlights())
			);
		});
	}

	@Override
	public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
		return providerMetricsService.record("AI_RESUME_QUESTIONS", PROVIDER, () -> {
			if (command == null || command.questionCount() <= 0) {
				return List.of();
			}
			String content = resumeQuestionAssistant.generate(
					command.resumeSummary(),
					command.keywords(),
					command.existingQuestionTitles(),
					command.missingKeywords(),
					command.questionCount()
			);
			List<ResumeQuestionOutput> rawQuestions = parseResumeQuestionOutputs(content);
			return rawQuestions.stream()
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
			String content = interviewReportExplanationAssistant.polish(
					defaultString(command.scope()),
					defaultString(command.title()),
					defaultString(command.prompt()),
					defaultString(command.level()),
					defaultString(command.summaryText()),
					command.evidencePoints(),
					command.improvementSuggestions()
			);
			InterviewReportExplanationResult output = parseJsonObject(content, InterviewReportExplanationResult.class);
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

	private <T> T parseJsonObject(String content, Class<T> type) {
		JsonNode node = parseJsonNode(content);
		if (node == null || !node.isObject()) {
			return null;
		}
		try {
			return objectMapper.treeToValue(node, type);
		} catch (Exception ex) {
			return null;
		}
	}

	private List<ResumeQuestionOutput> parseResumeQuestionOutputs(String content) {
		JsonNode node = parseJsonNode(content);
		if (node == null) {
			return List.of();
		}
		JsonNode questionsNode = node;
		if (node.isObject()) {
			questionsNode = node.path("questions");
		}
		if (!questionsNode.isArray()) {
			return List.of();
		}
		return StreamSupport.stream(questionsNode.spliterator(), false)
				.map(item -> {
					try {
						return objectMapper.treeToValue(item, ResumeQuestionOutput.class);
					} catch (Exception ex) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.toList();
	}

	private JsonNode parseJsonNode(String content) {
		if (content == null || content.isBlank()) {
			return null;
		}
		String normalized = stripCodeFence(content.trim());
		JsonNode directNode = readJsonNode(normalized);
		if (directNode != null) {
			return directNode;
		}
		int start = normalized.indexOf('{');
		int end = normalized.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return readJsonNode(normalized.substring(start, end + 1));
		}
		return null;
	}

	private JsonNode readJsonNode(String value) {
		try {
			return objectMapper.readTree(value);
		} catch (Exception ex) {
			return null;
		}
	}

	private String stripCodeFence(String value) {
		String normalized = value;
		if (normalized.startsWith("```")) {
			int firstNewLine = normalized.indexOf('\n');
			if (firstNewLine >= 0) {
				normalized = normalized.substring(firstNewLine + 1);
			}
		}
		if (normalized.endsWith("```")) {
			normalized = normalized.substring(0, normalized.length() - 3);
		}
		return normalized.trim();
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
