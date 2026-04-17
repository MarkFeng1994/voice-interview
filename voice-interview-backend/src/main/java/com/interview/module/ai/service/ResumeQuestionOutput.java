package com.interview.module.ai.service;

public record ResumeQuestionOutput(
		String title,
		String prompt,
		String targetKeyword,
		Integer difficulty
) {
}
