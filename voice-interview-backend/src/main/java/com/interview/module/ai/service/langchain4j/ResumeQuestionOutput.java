package com.interview.module.ai.service.langchain4j;

public record ResumeQuestionOutput(
		String title,
		String prompt,
		String targetKeyword,
		Integer difficulty
) {
}
