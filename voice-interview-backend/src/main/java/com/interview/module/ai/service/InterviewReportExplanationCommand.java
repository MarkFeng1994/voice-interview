package com.interview.module.ai.service;

import java.util.List;

public record InterviewReportExplanationCommand(
		String scope,
		String title,
		String prompt,
		String level,
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions
) {

	public InterviewReportExplanationCommand {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
	}
}
