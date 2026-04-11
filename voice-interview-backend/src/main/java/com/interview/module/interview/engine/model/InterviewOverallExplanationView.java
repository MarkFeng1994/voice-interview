package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewOverallExplanationView(
		String level,
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions,
		String generatedBy
) {

	public InterviewOverallExplanationView {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
		generatedBy = generatedBy == null ? "RULE" : generatedBy;
	}
}
