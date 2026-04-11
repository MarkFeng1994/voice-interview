package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

class InterviewReportExplanationServiceTest {

	@Test
	void should_build_rule_based_overall_explanation() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewOverallExplanationView explanation = service.buildOverallExplanation(
				68,
				List.of(
						new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 60, "核心点回答到了，但细节与例子还可以更深入。", null),
						new InterviewQuestionReportView(2, "消息队列", "请说明消息可靠性方案。", 72, "回答较完整。", null)
				),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 60, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略")),
						new InterviewRoundRecord("r2", 2, 0, "QUESTION", "题目", null, 0L, 72, "我们通过重试和死信队列保证消息可靠性。", null, "TEXT", "2026-04-11T00:01:00Z", "2026-04-11T00:01:10Z", "回答较完整", "NEXT_QUESTION", "当前回答已达到继续下一题的标准", List.of())
				)
		);

		assertThat(explanation.level()).isEqualTo("MEDIUM");
		assertThat(explanation.summaryText()).contains("整体");
		assertThat(explanation.evidencePoints()).isNotEmpty();
		assertThat(explanation.improvementSuggestions()).isNotEmpty();
		assertThat(explanation.generatedBy()).isEqualTo("RULE");
	}

	@Test
	void should_build_question_explanation_from_missing_points() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1),
				new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 60, "核心点回答到了，但细节与例子还可以更深入。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 60, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略"))
				)
		);

		assertThat(explanation.performanceLevel()).isEqualTo("MEDIUM");
		assertThat(explanation.summaryText()).contains("一致性策略");
		assertThat(explanation.evidencePoints()).contains("缺少关键点：一致性策略");
		assertThat(explanation.improvementSuggestion()).contains("一致性策略");
		assertThat(explanation.generatedBy()).isEqualTo("RULE");
	}
}
