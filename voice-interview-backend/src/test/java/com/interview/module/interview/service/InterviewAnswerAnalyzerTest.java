package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewAnswerAnalyzerTest {

	@Test
	void should_request_follow_up_when_key_points_are_missing() {
		InterviewAnswerAnalyzer analyzer = InterviewAnswerAnalyzer.heuristic();

		var analysis = analyzer.analyze(
				"请说明 Redis 在项目中的使用场景和一致性策略",
				"我们项目主要拿 Redis 做缓存",
				List.of("缓存场景", "一致性策略"));

		assertThat(analysis.followUpNeeded()).isTrue();
		assertThat(analysis.missingPoints()).contains("一致性策略");
	}
}
