package com.interview.module.interview.service;

public record FollowUpDecision(
		Action action,
		String direction,
		String reasonCode,
		String reasonText
) {

	public enum Action {
		FOLLOW_UP,
		NEXT_QUESTION,
		END_INTERVIEW
	}

	public static FollowUpDecision followUp(String direction, String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.FOLLOW_UP, direction, reasonCode, reasonText);
	}

	public static FollowUpDecision nextQuestion(String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.NEXT_QUESTION, null, reasonCode, reasonText);
	}

	public static FollowUpDecision endInterview(String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.END_INTERVIEW, null, reasonCode, reasonText);
	}
}
