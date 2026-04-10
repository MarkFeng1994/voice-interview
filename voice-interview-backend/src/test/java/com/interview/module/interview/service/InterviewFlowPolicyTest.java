package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterviewFlowPolicyTest {

	@Test
	void should_use_sixty_minute_defaults() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120);
		InterviewFlowPolicy.DurationProfile profile = policy.resolve(60);

		assertThat(profile.durationMinutes()).isEqualTo(60);
		assertThat(profile.mainQuestionCount()).isEqualTo(8);
		assertThat(profile.maxFollowUpPerQuestion()).isEqualTo(2);
	}

	@Test
	void should_expand_followups_for_two_hour_interview() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120);
		InterviewFlowPolicy.DurationProfile profile = policy.resolve(120);

		assertThat(profile.durationMinutes()).isEqualTo(120);
		assertThat(profile.mainQuestionCount()).isEqualTo(8);
		assertThat(profile.maxFollowUpPerQuestion()).isEqualTo(3);
	}
}
