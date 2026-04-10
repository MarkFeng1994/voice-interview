package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewQuestionComposerTest {

	@Test
	void should_prioritize_resume_and_library_questions_before_fallback() {
		InterviewQuestionComposer composer = InterviewQuestionComposer.inMemory(
				List.of(new InterviewQuestionComposer.CandidateQuestion("Redis场景", "请说明 Redis 的使用场景", "RESUME")),
				List.of(new InterviewQuestionComposer.CandidateQuestion("缓存一致性", "缓存一致性如何保证？", "LIBRARY"))
		);

		var result = composer.compose("JAVA_CORE", List.of("Redis"), List.of("backend-core"));

		assertThat(result).extracting(InterviewQuestionComposer.ComposedQuestion::source)
				.containsSequence("RESUME", "LIBRARY");
	}
}
