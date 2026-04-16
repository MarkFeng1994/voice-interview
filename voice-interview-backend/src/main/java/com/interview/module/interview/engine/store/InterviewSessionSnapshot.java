package com.interview.module.interview.engine.store;

import java.util.List;

import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;
import com.interview.module.interview.engine.model.RealtimeMetrics;

public record InterviewSessionSnapshot(
		String sessionId,
		String ownerUserId,
		String ownerNickname,
		List<InterviewQuestionSnapshot> questions,
		String stage,
		int durationMinutes,
		int maxFollowUpPerQuestion,
		Integer interviewerSpeakerId,
		Double interviewerSpeechSpeed,
		String status,
		int currentQuestionIndex,
		int followUpIndex,
		List<InterviewRoundRecord> rounds,
		String interviewMode,
		int realtimeTurnCount,
		RealtimeMetrics realtimeMetrics
) {

	public InterviewSessionSnapshot(
			String sessionId,
			String ownerUserId,
			String ownerNickname,
			List<InterviewQuestionSnapshot> questions,
			int maxFollowUpPerQuestion,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed,
			String status,
			int currentQuestionIndex,
			int followUpIndex,
			List<InterviewRoundRecord> rounds
	) {
		this(
				sessionId,
				ownerUserId,
				ownerNickname,
				questions,
				InterviewStage.OPENING.name(),
				60,
				maxFollowUpPerQuestion,
				interviewerSpeakerId,
				interviewerSpeechSpeed,
				status,
				currentQuestionIndex,
				followUpIndex,
				rounds,
				"standard",
				0,
				null
		);
	}
}
