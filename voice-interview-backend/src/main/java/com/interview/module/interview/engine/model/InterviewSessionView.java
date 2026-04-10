package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewSessionView(
		String sessionId,
		String status,
		String stage,
		int durationMinutes,
		int currentQuestionIndex,
		int totalQuestions,
		int followUpIndex,
		int maxFollowUpPerQuestion,
		String currentQuestionTitle,
		String currentQuestionPrompt,
		List<InterviewQuestionSnapshot> questions,
		List<InterviewRoundRecord> rounds,
		List<InterviewMessageView> messages
) {

	public InterviewSessionView(
			String sessionId,
			String status,
			int currentQuestionIndex,
			int totalQuestions,
			int followUpIndex,
			int maxFollowUpPerQuestion,
			String currentQuestionTitle,
			String currentQuestionPrompt,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds,
			List<InterviewMessageView> messages
	) {
		this(
				sessionId,
				status,
				InterviewStage.OPENING.name(),
				60,
				currentQuestionIndex,
				totalQuestions,
				followUpIndex,
				maxFollowUpPerQuestion,
				currentQuestionTitle,
				currentQuestionPrompt,
				questions,
				rounds,
				messages
		);
	}
}
