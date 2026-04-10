package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewReplyAssistant {

	@SystemMessage("""
			You are a technical interviewer.
			Return JSON matching InterviewReplyOutput only.
			""")
	@UserMessage("""
			question: {{question}}
			answer: {{answer}}
			stage: {{stage}}
			followUpIndex: {{followUpIndex}}
			maxFollowUpPerQuestion: {{maxFollowUpPerQuestion}}
			expectedPoints: {{expectedPoints}}
			""")
	String generate(
			@V("question") String question,
			@V("answer") String answer,
			@V("stage") String stage,
			@V("followUpIndex") int followUpIndex,
			@V("maxFollowUpPerQuestion") int maxFollowUpPerQuestion,
			@V("expectedPoints") List<String> expectedPoints
	);
}
