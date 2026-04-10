package com.interview.module.interview.service;

public class InterviewFlowPolicy {

	private final int defaultDurationMinutes;
	private final int maxDurationMinutes;

	public InterviewFlowPolicy(int defaultDurationMinutes, int maxDurationMinutes) {
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.maxDurationMinutes = maxDurationMinutes;
	}

	public DurationProfile resolve(Integer requestedMinutes) {
		int resolvedDuration = requestedMinutes == null ? defaultDurationMinutes : requestedMinutes;
		resolvedDuration = Math.max(defaultDurationMinutes, Math.min(maxDurationMinutes, resolvedDuration));
		if (resolvedDuration >= 120) {
			return new DurationProfile(120, 8, 3, 28);
		}
		if (resolvedDuration >= 90) {
			return new DurationProfile(90, 8, 3, 24);
		}
		return new DurationProfile(60, 8, 2, 20);
	}

	public record DurationProfile(
			int durationMinutes,
			int mainQuestionCount,
			int maxFollowUpPerQuestion,
			int maxTotalQuestions
	) {
	}
}
