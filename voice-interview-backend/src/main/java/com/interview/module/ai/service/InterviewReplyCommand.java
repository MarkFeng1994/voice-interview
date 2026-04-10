package com.interview.module.ai.service;

import java.util.List;

public record InterviewReplyCommand(
		String question,
		String answer,
		String stage,
		int followUpIndex,
		int maxFollowUpPerQuestion,
		List<String> expectedPoints
) {
	public InterviewReplyCommand {
		expectedPoints = expectedPoints == null ? List.of() : List.copyOf(expectedPoints);
	}
}
