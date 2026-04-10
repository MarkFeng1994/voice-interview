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
		String summary,
		String stage,
		Integer durationMinutes
) {

	public InterviewSessionSummaryView(
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
		this(sessionId, status, title, startedAt, lastUpdatedAt, totalQuestions, answeredRounds, overallScore, summary, null, null);
	}
}
