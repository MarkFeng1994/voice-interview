package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewQuestionExplanationView(
		String performanceLevel,
		String summaryText,
		List<String> evidencePoints,
		String improvementSuggestion,
		String generatedBy
) {

	public InterviewQuestionExplanationView {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		generatedBy = generatedBy == null ? "RULE" : generatedBy;
	}
}
