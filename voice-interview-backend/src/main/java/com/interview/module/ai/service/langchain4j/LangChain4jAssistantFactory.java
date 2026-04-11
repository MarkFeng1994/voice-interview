package com.interview.module.ai.service.langchain4j;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.interview.common.config.LangChain4jAiProperties;
import com.interview.common.config.OpenAiProperties;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "langchain4j")
public class LangChain4jAssistantFactory {

	private final InterviewReplyAssistant interviewReplyAssistant;
	private final ResumeKeywordAssistant resumeKeywordAssistant;
	private final ResumeQuestionAssistant resumeQuestionAssistant;
	private final InterviewReportExplanationAssistant interviewReportExplanationAssistant;

	public LangChain4jAssistantFactory(
			OpenAiProperties openAiProperties,
			LangChain4jAiProperties langChain4jAiProperties
	) {
		OpenAiChatModel chatModel = createChatModel(openAiProperties, langChain4jAiProperties);
		// Future agent work will attach tools/chatMemory/RAG here.
		this.interviewReplyAssistant = AiServices.create(InterviewReplyAssistant.class, chatModel);
		this.resumeKeywordAssistant = AiServices.create(ResumeKeywordAssistant.class, chatModel);
		this.resumeQuestionAssistant = AiServices.create(ResumeQuestionAssistant.class, chatModel);
		this.interviewReportExplanationAssistant = AiServices.create(InterviewReportExplanationAssistant.class, chatModel);
	}

	public InterviewReplyAssistant interviewReplyAssistant() {
		return interviewReplyAssistant;
	}

	public ResumeKeywordAssistant resumeKeywordAssistant() {
		return resumeKeywordAssistant;
	}

	public ResumeQuestionAssistant resumeQuestionAssistant() {
		return resumeQuestionAssistant;
	}

	public InterviewReportExplanationAssistant interviewReportExplanationAssistant() {
		return interviewReportExplanationAssistant;
	}

	private OpenAiChatModel createChatModel(
			OpenAiProperties openAiProperties,
			LangChain4jAiProperties langChain4jAiProperties
	) {
		return OpenAiChatModel.builder()
				.apiKey(openAiProperties.resolveAiApiKey())
				.baseUrl(openAiProperties.resolveAiBaseUrl())
				.modelName(openAiProperties.resolveAiModel())
				.temperature(langChain4jAiProperties.getTemperature())
				.timeout(Duration.ofSeconds(Math.max(1, langChain4jAiProperties.getTimeoutSeconds())))
				.maxRetries(Math.max(0, langChain4jAiProperties.getMaxRetries()))
				.build();
	}
}
