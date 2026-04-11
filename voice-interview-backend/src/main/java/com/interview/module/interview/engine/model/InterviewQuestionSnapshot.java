package com.interview.module.interview.engine.model;

public record InterviewQuestionSnapshot(
		int questionIndex,
		String titleSnapshot,
		String promptSnapshot,
		String sourceSnapshot,
		Integer difficultySnapshot
) {
	public InterviewQuestionSnapshot(int questionIndex, String titleSnapshot, String promptSnapshot) {
		this(questionIndex, titleSnapshot, promptSnapshot, "PRESET", 1);
	}
}
