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
}
