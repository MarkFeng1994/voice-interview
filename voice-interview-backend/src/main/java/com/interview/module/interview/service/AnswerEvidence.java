package com.interview.module.interview.service;

import java.util.List;

public record AnswerEvidence(
		boolean answered,
		Completeness completeness,
		Depth depth,
		CorrectnessRisk correctnessRisk,
		List<String> missingPoints,
		String recommendedFollowUpDirection,
		List<String> reasonCodes,
		String summaryReason
) {

	public AnswerEvidence {
		missingPoints = missingPoints == null ? List.of() : List.copyOf(missingPoints);
		reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
		recommendedFollowUpDirection = recommendedFollowUpDirection == null ? "NEXT_QUESTION" : recommendedFollowUpDirection;
		summaryReason = summaryReason == null ? "" : summaryReason;
	}

	public boolean followUpNeeded() {
		return !"NEXT_QUESTION".equalsIgnoreCase(recommendedFollowUpDirection);
	}

	public String followUpDirection() {
		return recommendedFollowUpDirection;
	}

	public String reason() {
		return summaryReason;
	}

	public enum Completeness {
		HIGH, MEDIUM, LOW
	}

	public enum Depth {
		DEEP, NORMAL, SHALLOW
	}

	public enum CorrectnessRisk {
		CONSISTENT, SUSPECTED_CONTRADICTION, CLEARLY_WRONG
	}
}
