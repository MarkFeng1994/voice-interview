package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
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

	@Test
	void should_build_question_explanation_for_depth_gap_from_analysis_reason() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "分布式事务", "请说明你们系统里分布式事务的处理方案。", "PRESET", 1),
				new InterviewQuestionReportView(1, "分布式事务", "请说明你们系统里分布式事务的处理方案。", 66, "回答到了主线，但细节不足。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 66, "我们用了最终一致性。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "回答偏结论化，缺少过程细节和案例支撑", "FOLLOW_UP", "细节不足，需要补充实际处理过程", List.of())
				)
		);

		assertThat(explanation.performanceLevel()).isEqualTo("MEDIUM");
		assertThat(explanation.summaryText()).contains("深度").contains("细节");
		assertThat(explanation.evidencePoints()).contains("回答偏结论化，缺少过程细节和案例支撑");
		assertThat(explanation.improvementSuggestion()).contains("案例");
	}

	@Test
	void should_build_question_explanation_for_risk_signal() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "消息队列", "请说明消息队列削峰和解耦的区别。", "PRESET", 1),
				new InterviewQuestionReportView(1, "消息队列", "请说明消息队列削峰和解耦的区别。", 58, "回答有明显偏差。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 58, "我们主要讲了数据库分库分表。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "回答有答偏风险，和题目核心不一致", "FOLLOW_UP", "需要回到题目本身重新回答", List.of())
				)
		);

		assertThat(explanation.performanceLevel()).isEqualTo("WEAK");
		assertThat(explanation.summaryText()).contains("答偏风险");
		assertThat(explanation.evidencePoints()).contains("分析中出现了答偏或前后不一致风险信号。");
		assertThat(explanation.improvementSuggestion()).contains("题干").contains("结论");
	}

	@Test
	void should_prioritize_risk_signal_over_missing_points_when_both_exist() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "消息队列", "请说明消息队列削峰和解耦的区别。", "PRESET", 1),
				new InterviewQuestionReportView(1, "消息队列", "请说明消息队列削峰和解耦的区别。", 55, "回答偏题。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 55, "我们主要通过乐观锁控制并发。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "回答与题目核心不一致", "FOLLOW_UP", "需要回到题目本身重新回答", List.of("削峰", "解耦"))
				)
		);

		assertThat(explanation.summaryText()).contains("答偏风险");
		assertThat(explanation.summaryText()).doesNotContain("缺少对");
		assertThat(explanation.improvementSuggestion()).contains("题干");
	}

	@Test
	void should_build_question_explanation_for_strong_answer() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 缓存击穿和穿透的处理方式。", "PRESET", 1),
				new InterviewQuestionReportView(1, "Redis", "请说明 Redis 缓存击穿和穿透的处理方式。", 88, "回答较完整。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 88, "我们会分别用互斥锁、布隆过滤器和空值缓存处理。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "回答较完整，核心点覆盖到位", "NEXT_QUESTION", "当前回答已达到继续下一题的标准", List.of())
				)
		);

		assertThat(explanation.performanceLevel()).isEqualTo("STRONG");
		assertThat(explanation.summaryText()).contains("回答较完整");
		assertThat(explanation.evidencePoints()).contains("得分较高且额外追问较少，说明回答完整度和稳定性都不错。");
		assertThat(explanation.improvementSuggestion()).contains("表达结构");
	}

	@Test
	void should_build_question_explanation_for_unanswered_or_unscored_question() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "缓存设计", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1),
				new InterviewQuestionReportView(1, "缓存设计", "请说明 Redis 的使用场景和一致性策略。", null, "当前题目还没有形成有效评分。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, null, null, null, null, "2026-04-11T00:00:00Z", null, null, null, null, List.of())
				)
		);

		assertThat(explanation.performanceLevel()).isNull();
		assertThat(explanation.summaryText()).containsAnyOf("未作答", "数据不足", "尚未形成有效评分");
		assertThat(explanation.summaryText()).doesNotContain("基础回答有了");
		assertThat(explanation.improvementSuggestion()).containsAnyOf("补充作答", "形成有效评分", "重新回答");
	}

	@Test
	void should_mark_explanations_as_rule_plus_llm_when_ai_polish_succeeds() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("OVERALL".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的整体总结",
								List.of("[E1] LLM 润色后的整体证据 1", "[E2] LLM 润色后的整体证据 2"),
								List.of("[S1] LLM 润色后的整体建议 1", "[S2] LLM 润色后的整体建议 2")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的分题总结",
							List.of("[E1] LLM 润色后的分题证据"),
							List.of("[S1] LLM 润色后的分题建议")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enrichedReport.overallExplanation().summaryText()).isEqualTo("LLM 润色后的整体总结");
		assertThat(enrichedReport.overallExplanation().evidencePoints()).containsExactly("LLM 润色后的整体证据 1", "LLM 润色后的整体证据 2");
		assertThat(enrichedReport.overallExplanation().evidencePoints()).allSatisfy(item -> assertThat(item).doesNotContain("[E"));
		assertThat(enrichedReport.overallExplanation().improvementSuggestions()).containsExactly("LLM 润色后的整体建议 1", "LLM 润色后的整体建议 2");
		assertThat(enrichedReport.overallExplanation().improvementSuggestions()).allSatisfy(item -> assertThat(item).doesNotContain("[S"));
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enrichedReport.questionReports().get(0).explanation().summaryText()).isEqualTo("LLM 润色后的分题总结");
		assertThat(enrichedReport.questionReports().get(0).explanation().evidencePoints()).containsExactly("LLM 润色后的分题证据");
		assertThat(enrichedReport.questionReports().get(0).explanation().evidencePoints()).allSatisfy(item -> assertThat(item).doesNotContain("[E"));
		assertThat(enrichedReport.questionReports().get(0).explanation().improvementSuggestion()).isEqualTo("LLM 润色后的分题建议");
		assertThat(enrichedReport.questionReports().get(0).explanation().improvementSuggestion()).doesNotContain("[S");

		ArgumentCaptor<InterviewReportExplanationCommand> commandCaptor = ArgumentCaptor.forClass(InterviewReportExplanationCommand.class);
		verify(aiService, times(2)).polishInterviewReportExplanation(commandCaptor.capture());
		assertThat(commandCaptor.getAllValues())
				.anySatisfy(command -> {
					assertThat(command.scope()).isEqualTo("OVERALL");
					assertThat(command.evidencePoints()).containsExactly(
							"[E1] 共有 1 个答题轮次暴露出关键点缺失，回答覆盖度还不够稳定。",
							"[E2] 本轮触发了 1 次继续追问，说明部分题目需要靠补充说明才能站稳。"
					);
					assertThat(command.improvementSuggestions()).containsExactly(
							"[S1] 按题型整理每题必须覆盖的关键点，先保证回答完整度。",
							"[S2] 高频题准备“背景、方案、取舍、结果”的固定表达，提升追问稳定性。"
					);
				})
				.anySatisfy(command -> {
					assertThat(command.scope()).isEqualTo("QUESTION");
					assertThat(command.evidencePoints()).containsExactly("[E1] 缺少关键点：一致性策略");
					assertThat(command.improvementSuggestions()).containsExactly("[S1] 补充 一致性策略，并明确你的方案、取舍和落地方式。");
				});
	}

	@Test
	void should_fallback_to_rule_explanations_when_ai_polish_throws() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any())).thenThrow(new IllegalStateException("boom"));
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.overallExplanation().summaryText()).contains("整体");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.questionReports().get(0).explanation().summaryText()).contains("一致性策略");
	}

	@Test
	void should_fallback_to_rule_overall_explanation_when_polish_changes_item_counts() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("OVERALL".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的整体总结",
								List.of("只返回一条整体证据"),
								List.of("LLM 润色后的整体建议 1", "LLM 润色后的整体建议 2")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的分题总结",
							List.of("[E1] LLM 润色后的分题证据"),
							List.of("[S1] LLM 润色后的分题建议")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.overallExplanation().summaryText()).contains("整体");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
	}

	@Test
	void should_fallback_to_rule_overall_explanation_when_polish_reorders_slots() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("OVERALL".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的整体总结",
								List.of("[E2] LLM 润色后的整体证据 2", "[E1] LLM 润色后的整体证据 1"),
								List.of("[S1] LLM 润色后的整体建议 1", "[S2] LLM 润色后的整体建议 2")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的分题总结",
							List.of("[E1] LLM 润色后的分题证据"),
							List.of("[S1] LLM 润色后的分题建议")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.overallExplanation().summaryText()).contains("整体");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
	}

	@Test
	void should_fallback_to_rule_question_explanation_when_polish_returns_multiple_suggestions() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("QUESTION".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的分题总结",
								List.of("LLM 润色后的分题证据"),
								List.of("LLM 润色后的分题建议 1", "LLM 润色后的分题建议 2")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的整体总结",
							List.of("[E1] LLM 润色后的整体证据 1", "[E2] LLM 润色后的整体证据 2"),
							List.of("[S1] LLM 润色后的整体建议 1", "[S2] LLM 润色后的整体建议 2")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.questionReports().get(0).explanation().summaryText()).contains("一致性策略");
	}

	@Test
	void should_fallback_to_rule_question_explanation_when_polish_changes_evidence_count() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("QUESTION".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的分题总结",
								List.of("LLM 润色后的分题证据 1", "LLM 润色后的分题证据 2"),
								List.of("LLM 润色后的分题建议")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的整体总结",
							List.of("[E1] LLM 润色后的整体证据 1", "[E2] LLM 润色后的整体证据 2"),
							List.of("[S1] LLM 润色后的整体建议 1", "[S2] LLM 润色后的整体建议 2")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"题目",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-11T00:00:00Z",
						"2026-04-11T00:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.questionReports().get(0).explanation().summaryText()).contains("一致性策略");
	}

	@Test
	void should_fallback_to_rule_question_explanation_when_polish_reorders_slots() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenAnswer(invocation -> {
					InterviewReportExplanationCommand command = invocation.getArgument(0, InterviewReportExplanationCommand.class);
					if ("QUESTION".equals(command.scope())) {
						return new InterviewReportExplanationResult(
								"LLM 润色后的分题总结",
								List.of("[E2] LLM 润色后的分题证据 2", "[E1] LLM 润色后的分题证据 1"),
								List.of("[S1] LLM 润色后的分题建议")
						);
					}
					return new InterviewReportExplanationResult(
							"LLM 润色后的整体总结",
							List.of("[E1] LLM 润色后的整体证据 1"),
							List.of("[S1] LLM 润色后的整体建议 1")
					);
				});
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView enrichedReport = service.enrichReport(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"分布式事务",
						68,
						"整体基础可用。",
						List.of("优点"),
						List.of("短板"),
						List.of("建议"),
						List.of(new InterviewQuestionReportView(
								1,
								"分布式事务",
								"请说明你们系统里分布式事务的处理方案。",
								66,
								"回答到了主线，但细节不足。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(1, "分布式事务", "请说明你们系统里分布式事务的处理方案。", "PRESET", 1)),
				List.of(
						new InterviewRoundRecord(
								"r1",
								1,
								0,
								"QUESTION",
								"题目",
								null,
								0L,
								66,
								"我们用了最终一致性。",
								null,
								"TEXT",
								"2026-04-11T00:00:00Z",
								"2026-04-11T00:00:10Z",
								"回答偏结论化，缺少过程细节和案例支撑",
								"FOLLOW_UP",
								"细节不足，需要补充实际处理过程",
								List.of()
						),
						new InterviewRoundRecord(
								"r2",
								1,
								1,
								"FOLLOW_UP",
								"继续展开",
								null,
								0L,
								66,
								"我们补充了消息补偿和状态推进。",
								null,
								"TEXT",
								"2026-04-11T00:00:11Z",
								"2026-04-11T00:00:20Z",
								"",
								"FOLLOW_UP",
								"还需要继续展开过程细节",
								List.of()
						)
				)
		);

		assertThat(enrichedReport.overallExplanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enrichedReport.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
		assertThat(enrichedReport.questionReports().get(0).explanation().summaryText()).contains("深度").contains("细节");
	}
}
