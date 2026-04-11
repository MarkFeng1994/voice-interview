package com.interview.module.ai.service;

import java.util.List;

public record InterviewReportExplanationResult(
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions
) {

	public InterviewReportExplanationResult {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
	}
}
