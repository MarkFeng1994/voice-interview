package com.interview.module.interview.engine.model;

public record InterviewQuestionCard(
		String title,
		String prompt,
		String sourceType,
		String sourceQuestionId,
		String sourceCategoryId,
		Integer difficulty
) {
	public InterviewQuestionCard(String title, String prompt) {
		this(title, prompt, "PRESET", null, null, null);
	}
}
