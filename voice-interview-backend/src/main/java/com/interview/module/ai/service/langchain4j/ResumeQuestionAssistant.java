package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResumeQuestionAssistant {

	@SystemMessage("""
			你是技术面试出题助手。
			只返回符合 ResumeQuestionListOutput 的 JSON。
			""")
	@UserMessage("""
			resumeSummary: {{resumeSummary}}
			keywords: {{keywords}}
			existingQuestionTitles: {{existingQuestionTitles}}
			missingKeywords: {{missingKeywords}}
			questionCount: {{questionCount}}
			""")
	ResumeQuestionListOutput generate(
			@V("resumeSummary") String resumeSummary,
			@V("keywords") List<String> keywords,
			@V("existingQuestionTitles") List<String> existingQuestionTitles,
			@V("missingKeywords") List<String> missingKeywords,
			@V("questionCount") int questionCount
	);
}
