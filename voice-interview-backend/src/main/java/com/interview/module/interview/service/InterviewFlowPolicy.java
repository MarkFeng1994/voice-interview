package com.interview.module.interview.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

@Service
public class InterviewFlowPolicy {

	private final int defaultDurationMinutes;
	private final int maxDurationMinutes;
	private final int normalFollowUpLimit;
	private final int highValueFollowUpLimit;
	private final int openingFollowUpLimit;
	private final int wrapUpFollowUpLimit;

	@Autowired
	public InterviewFlowPolicy(
			@Value("${app.interview.default-duration-minutes:60}") int defaultDurationMinutes,
			@Value("${app.interview.max-duration-minutes:120}") int maxDurationMinutes,
			@Value("${app.interview.follow-up.normal-limit:1}") int normalFollowUpLimit,
			@Value("${app.interview.follow-up.high-value-limit:2}") int highValueFollowUpLimit,
			@Value("${app.interview.follow-up.opening-limit:1}") int openingFollowUpLimit,
			@Value("${app.interview.follow-up.wrap-up-limit:0}") int wrapUpFollowUpLimit
	) {
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.maxDurationMinutes = maxDurationMinutes;
		this.normalFollowUpLimit = normalFollowUpLimit;
		this.highValueFollowUpLimit = highValueFollowUpLimit;
		this.openingFollowUpLimit = openingFollowUpLimit;
		this.wrapUpFollowUpLimit = wrapUpFollowUpLimit;
	}

	InterviewFlowPolicy(int defaultDurationMinutes, int maxDurationMinutes) {
		this(defaultDurationMinutes, maxDurationMinutes, 1, 2, 1, 0);
	}

	public int resolveFollowUpLimit(String stage, boolean highValueQuestion, int sessionMaxFollowUp) {
		int configuredLimit;
		if ("WRAP_UP".equals(stage)) {
			configuredLimit = wrapUpFollowUpLimit;
		} else if ("OPENING".equals(stage)) {
			configuredLimit = openingFollowUpLimit;
		} else if (highValueQuestion) {
			configuredLimit = highValueFollowUpLimit;
		} else {
			configuredLimit = normalFollowUpLimit;
		}
		return Math.max(0, Math.min(sessionMaxFollowUp, configuredLimit));
	}

	public boolean isHighValueQuestion(String stage, InterviewQuestionSnapshot question) {
		if ("PROJECT_DEEP_DIVE".equals(stage)) {
			return true;
		}
		if (question == null) {
			return false;
		}
		if (question.difficultySnapshot() != null && question.difficultySnapshot() >= 3) {
			return true;
		}
		if ("AI_RESUME".equalsIgnoreCase(question.sourceSnapshot())) {
			return true;
		}
		String normalized = ((question.titleSnapshot() == null ? "" : question.titleSnapshot()) + " "
				+ (question.promptSnapshot() == null ? "" : question.promptSnapshot()))
				.replaceAll("\\s+", "")
				.toLowerCase();
		return normalized.contains("项目")
				|| normalized.contains("一致性")
				|| normalized.contains("并发")
				|| normalized.contains("事务");
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
