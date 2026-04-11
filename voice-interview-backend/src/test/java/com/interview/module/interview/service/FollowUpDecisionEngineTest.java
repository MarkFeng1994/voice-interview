package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

class FollowUpDecisionEngineTest {

	@Test
	void should_follow_up_for_normal_question_when_key_points_are_missing() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.MEDIUM,
				AnswerEvidence.Depth.NORMAL,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of("一致性策略"),
				"MISSING_KEY_POINT",
				List.of("KEY_POINT_MISSING"),
				"缺少关键点：一致性策略"
		);

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "Redis", "请说明使用场景和一致性策略", "PRESET", 1),
				"JAVA_CORE",
				0,
				2,
				evidence
		);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.FOLLOW_UP);
		assertThat(decision.direction()).isEqualTo("MISSING_KEY_POINT");
	}

	@Test
	void should_allow_second_follow_up_for_high_value_question() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.HIGH,
				AnswerEvidence.Depth.SHALLOW,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of(),
				"NEED_EXAMPLE_OR_DETAIL",
				List.of("DEPTH_SHALLOW"),
				"回答结论化，缺少过程和细节"
		);

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "订单系统深挖", "请详细说明最终一致性方案", "AI_RESUME", 3),
				"PROJECT_DEEP_DIVE",
				1,
				3,
				evidence
		);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.FOLLOW_UP);
		assertThat(decision.reasonCode()).isEqualTo("HIGH_VALUE_DEPTH_PROBE");
	}

	@Test
	void should_skip_follow_up_in_wrap_up_stage() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.MEDIUM,
				AnswerEvidence.Depth.SHALLOW,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of("结果"),
				"MISSING_KEY_POINT",
				List.of("KEY_POINT_MISSING", "DEPTH_SHALLOW"),
				"缺少关键点：结果"
		);

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "收尾总结", "请总结本项目复盘结论", "PRESET", 1),
				"WRAP_UP",
				0,
				2,
				evidence
		);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.NEXT_QUESTION);
		assertThat(decision.reasonCode()).isEqualTo("WRAP_UP_SKIP_FOLLOW_UP");
	}
}
