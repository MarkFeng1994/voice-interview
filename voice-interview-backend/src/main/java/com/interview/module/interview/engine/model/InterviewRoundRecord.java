package com.interview.module.interview.engine.model;

public record InterviewRoundRecord(
		String roundId,
		int questionIndex,
		int followUpIndex,
		String roundType,
		String aiMessageText,
		String aiAudioUrl,
		long aiAudioDurationMs,
		Integer scoreSuggestion,
		String userAnswerText,
		String userAudioUrl,
		String userAnswerMode,
		String createdAt,
		String answeredAt
) {

	public InterviewRoundRecord withUserAnswer(
			String userAnswerText,
			String userAudioUrl,
			String userAnswerMode,
			String answeredAt
	) {
		return new InterviewRoundRecord(
				roundId,
				questionIndex,
				followUpIndex,
				roundType,
				aiMessageText,
				aiAudioUrl,
				aiAudioDurationMs,
				scoreSuggestion,
				userAnswerText,
				userAudioUrl,
				userAnswerMode,
				createdAt,
				answeredAt
		);
	}
}
