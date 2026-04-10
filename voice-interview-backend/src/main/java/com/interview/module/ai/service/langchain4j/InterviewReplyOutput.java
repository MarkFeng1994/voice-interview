package com.interview.module.ai.service.langchain4j;

public record InterviewReplyOutput(
		String spokenText,
		String decisionSuggestion,
		Integer scoreSuggestion
) {
}
