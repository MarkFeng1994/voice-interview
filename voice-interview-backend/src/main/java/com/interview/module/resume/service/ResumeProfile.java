package com.interview.module.resume.service;

import java.util.List;

public record ResumeProfile(
		String resumeId,
		String userId,
		String mediaFileId,
		String originalFileName,
		String contentType,
		long sizeBytes,
		String parseStatus,
		String resumeSummary,
		List<String> extractedKeywords,
		List<String> projectHighlights,
		String parseError
) {
}
