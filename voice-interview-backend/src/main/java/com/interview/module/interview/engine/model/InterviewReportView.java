package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewReportView(
		String sessionId,
		String status,
		String title,
		Integer overallScore,
		String overallComment,
		List<String> strengths,
		List<String> weaknesses,
		List<String> suggestions,
		List<InterviewQuestionReportView> questionReports,
		InterviewOverallExplanationView overallExplanation,
		RealtimeMetricsView realtimeMetrics
) {
}
