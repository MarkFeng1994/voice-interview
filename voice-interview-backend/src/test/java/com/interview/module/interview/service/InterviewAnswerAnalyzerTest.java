package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewAnswerAnalyzerTest {

	@Test
	void should_mark_medium_completeness_when_core_points_are_missing() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明 Redis 在项目中的使用场景和一致性策略",
				"我们项目主要拿 Redis 做缓存",
				List.of("缓存场景", "一致性策略"));

		assertThat(evidence.completeness()).isEqualTo(AnswerEvidence.Completeness.MEDIUM);
		assertThat(evidence.missingPoints()).contains("一致性策略");
		assertThat(evidence.recommendedFollowUpDirection()).isEqualTo("MISSING_KEY_POINT");
	}

	@Test
	void should_mark_shallow_depth_when_answer_has_only_conclusion() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明你如何处理库存扣减并发问题",
				"我会用乐观锁。",
				List.of("并发控制"));

		assertThat(evidence.depth()).isEqualTo(AnswerEvidence.Depth.SHALLOW);
		assertThat(evidence.reasonCodes()).contains("DEPTH_SHALLOW");
	}

	@Test
	void should_mark_suspected_contradiction_when_answer_contains_mutually_exclusive_claims() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明你们系统是否用了消息队列",
				"我们没有使用消息队列，但是所有异步解耦都通过 Kafka 完成。",
				List.of("消息队列"));

		assertThat(evidence.correctnessRisk()).isEqualTo(AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION);
		assertThat(evidence.recommendedFollowUpDirection()).isEqualTo("CLARIFY_CONTRADICTION");
	}
}
