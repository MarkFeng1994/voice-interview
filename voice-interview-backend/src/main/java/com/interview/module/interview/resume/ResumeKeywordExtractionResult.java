package com.interview.module.interview.resume;

import java.util.List;
import java.util.Objects;

public record ResumeKeywordExtractionResult(
		String summary,
		List<String> keywords,
		List<String> experienceHighlights
) {

	public ResumeKeywordExtractionResult {
		summary = summary == null ? "" : summary.trim();
		keywords = normalize(keywords);
		experienceHighlights = normalize(experienceHighlights);
	}

	private static List<String> normalize(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.distinct()
				.toList();
	}

	public boolean hasKeywords() {
		return !keywords.isEmpty();
	}
}
