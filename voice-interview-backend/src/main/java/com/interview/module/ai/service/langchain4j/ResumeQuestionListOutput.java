package com.interview.module.ai.service.langchain4j;

import java.util.List;

public record ResumeQuestionListOutput(
		List<ResumeQuestionOutput> questions
) {
}
