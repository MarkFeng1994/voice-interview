package com.interview.module.interview.resume;

import java.util.List;
import java.util.Objects;

import com.interview.module.interview.engine.model.InterviewQuestionCard;

public record ResumeInterviewPlan(
		String resumeFileId,
		String resumeSummary,
		List<String> extractedKeywords,
		List<String> matchedCategoryNames,
		List<String> matchedLibraryQuestionTitles,
		List<String> missingKeywords,
		List<GeneratedResumeQuestion> generatedQuestions,
		List<InterviewQuestionCard> questions,
		boolean usedPresetFallback
) {

	public ResumeInterviewPlan {
		resumeFileId = resumeFileId == null ? null : resumeFileId.trim();
		resumeSummary = resumeSummary == null ? "" : resumeSummary.trim();
		extractedKeywords = normalizeStrings(extractedKeywords);
		matchedCategoryNames = normalizeStrings(matchedCategoryNames);
		matchedLibraryQuestionTitles = normalizeStrings(matchedLibraryQuestionTitles);
		missingKeywords = normalizeStrings(missingKeywords);
		generatedQuestions = generatedQuestions == null ? List.of() : generatedQuestions.stream().filter(Objects::nonNull).toList();
		questions = questions == null ? List.of() : questions.stream().filter(Objects::nonNull).toList();
	}

	private static List<String> normalizeStrings(List<String> values) {
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
