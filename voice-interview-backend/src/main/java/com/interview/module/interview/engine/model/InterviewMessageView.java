package com.interview.module.interview.engine.model;

public record InterviewMessageView(
		String id,
		String role,
		String speaker,
		String text,
		String roundType,
		int questionIndex,
		int followUpIndex,
		String audioUrl,
		long audioDurationMs,
		Integer scoreSuggestion,
		String answerMode,
		String createdAt
) {
}
