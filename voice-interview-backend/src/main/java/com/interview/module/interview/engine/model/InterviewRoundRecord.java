package com.interview.module.interview.engine.model;

import java.util.List;

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
		String answeredAt,
		String analysisReason,
		String followUpDecision,
		String followUpDecisionReason,
		List<String> missingPointsSnapshot
) {

	public InterviewRoundRecord {
		missingPointsSnapshot = missingPointsSnapshot == null ? List.of() : List.copyOf(missingPointsSnapshot);
	}

	public InterviewRoundRecord withUserAnswer(
			String userAnswerText,
			String userAudioUrl,
			String userAnswerMode,
			String answeredAt,
			String analysisReason,
			String followUpDecision,
			String followUpDecisionReason,
			List<String> missingPointsSnapshot
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
				answeredAt,
				analysisReason,
				followUpDecision,
				followUpDecisionReason,
				missingPointsSnapshot
		);
	}
}
