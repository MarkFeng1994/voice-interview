package com.interview.module.interview.resume;

public record GeneratedResumeQuestion(
		String title,
		String prompt,
		String targetKeyword,
		Integer difficulty
) {

	public GeneratedResumeQuestion {
		title = title == null ? "" : title.trim();
		prompt = prompt == null ? "" : prompt.trim();
		targetKeyword = targetKeyword == null ? null : targetKeyword.trim();
		difficulty = difficulty == null
				? null
				: Math.max(1, Math.min(3, difficulty));
	}
}
