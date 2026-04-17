package com.interview.module.ai.service;

public record InterviewReplyOutput(
		String spokenText,
		String decisionSuggestion,
		Integer scoreSuggestion
) {
}
