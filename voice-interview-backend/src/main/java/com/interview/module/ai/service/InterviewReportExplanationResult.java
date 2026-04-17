package com.interview.module.ai.service;

import java.util.List;

public record InterviewReportExplanationResult(
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions
) {

	public InterviewReportExplanationResult {
		evidencePoints = normalizeStringList(evidencePoints);
		improvementSuggestions = normalizeStringList(improvementSuggestions);
	}

	private static List<String> normalizeStringList(List<String> list) {
		if (list == null || list.isEmpty()) {
			return List.of();
		}
		return list.stream()
				.filter(java.util.Objects::nonNull)
				.map(String::trim)
				.filter(v -> !v.isBlank())
				.toList();
	}
}
