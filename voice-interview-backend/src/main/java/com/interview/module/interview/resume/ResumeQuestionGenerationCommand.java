package com.interview.module.interview.resume;

import java.util.List;
import java.util.Objects;

public record ResumeQuestionGenerationCommand(
		String resumeSummary,
		List<String> keywords,
		List<String> existingQuestionTitles,
		List<String> missingKeywords,
		int questionCount
) {

	public ResumeQuestionGenerationCommand {
		resumeSummary = resumeSummary == null ? "" : resumeSummary.trim();
		keywords = normalize(keywords);
		existingQuestionTitles = normalize(existingQuestionTitles);
		missingKeywords = normalize(missingKeywords);
		questionCount = Math.max(0, questionCount);
	}

	private static List<String> normalize(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.toList();
	}
}
