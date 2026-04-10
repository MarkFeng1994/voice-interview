package com.interview.module.ai.service;

public record AiReply(
		String spokenText,
		String decisionSuggestion,
		Integer scoreSuggestion
) {
}
