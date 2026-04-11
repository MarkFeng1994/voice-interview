package com.interview.module.interview.engine.model;

public record InterviewQuestionReportView(
		int questionIndex,
		String title,
		String prompt,
		Integer score,
		String summary,
		InterviewQuestionExplanationView explanation
) {
}
