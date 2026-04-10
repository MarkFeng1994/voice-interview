package com.interview.module.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InterviewFlowPolicy {

	private final int defaultDurationMinutes;
	private final int maxDurationMinutes;

	public InterviewFlowPolicy(
			@Value("${app.interview.default-duration-minutes:60}") int defaultDurationMinutes,
			@Value("${app.interview.max-duration-minutes:120}") int maxDurationMinutes
	) {
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
