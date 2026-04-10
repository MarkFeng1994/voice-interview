package com.interview.module.interview.engine.model;

public record InterviewSessionSummaryView(
		String sessionId,
		String status,
		String title,
		String startedAt,
		String lastUpdatedAt,
		int totalQuestions,
		int answeredRounds,
		Integer overallScore,
		String summary
) {
}
