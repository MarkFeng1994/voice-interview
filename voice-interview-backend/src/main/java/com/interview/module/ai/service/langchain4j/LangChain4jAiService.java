package com.interview.module.ai.service.langchain4j;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
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
	private final ProviderMetricsService providerMetricsService;

	public LangChain4jAiService(
			LangChain4jAssistantFactory assistantFactory,
			ProviderMetricsService providerMetricsService
	) {
		this.interviewReplyAssistant = assistantFactory.interviewReplyAssistant();
		this.resumeKeywordAssistant = assistantFactory.resumeKeywordAssistant();
		this.resumeQuestionAssistant = assistantFactory.resumeQuestionAssistant();
		this.providerMetricsService = providerMetricsService;
	}

	@Override
	public AiReply generateInterviewReply(InterviewReplyCommand command) {
		return providerMetricsService.record("AI", PROVIDER, () -> {
			InterviewReplyOutput output = interviewReplyAssistant.generate(
					command == null ? "" : command.question(),
					command == null ? "" : command.answer(),
					command == null ? "" : command.stage(),
					command == null ? 0 : command.followUpIndex(),
					command == null ? 0 : command.maxFollowUpPerQuestion(),
					command == null ? List.of() : command.expectedPoints()
			);
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
			ResumeKeywordOutput output = resumeKeywordAssistant.extract(resumeText == null ? "" : resumeText);
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
			ResumeQuestionListOutput output = resumeQuestionAssistant.generate(
					command.resumeSummary(),
					command.keywords(),
					command.existingQuestionTitles(),
					command.missingKeywords(),
					command.questionCount()
			);
			List<ResumeQuestionOutput> rawQuestions = output == null || output.questions() == null
					? List.of()
					: output.questions();
			return rawQuestions.stream()
					.limit(command.questionCount())
					.map(this::toGeneratedQuestion)
					.toList();
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

	private String firstNonBlank(String value, String fallback) {
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		return fallback;
	}
}
