package com.interview.module.ai.service.langchain4j;

import java.util.List;

public record ResumeKeywordOutput(
		String summary,
		List<String> keywords,
		List<String> experienceHighlights
) {
}
