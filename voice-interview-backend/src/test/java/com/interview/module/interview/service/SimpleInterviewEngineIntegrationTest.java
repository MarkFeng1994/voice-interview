package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.entity.SessionEntity;
import com.interview.module.interview.entity.SessionQuestionEntity;
import com.interview.module.interview.engine.SimpleInterviewEngine;
import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.model.InterviewRoundRecord;
import com.interview.module.interview.engine.store.InterviewReportStore;
import com.interview.module.interview.engine.store.PersistedInterviewReport;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;
import com.interview.module.interview.engine.store.JdbcInterviewSessionStore;
import com.interview.module.interview.engine.store.NoopInterviewReportStore;
import com.interview.module.interview.mapper.RoundMapper;
import com.interview.module.interview.mapper.SessionMapper;
import com.interview.module.interview.mapper.SessionQuestionMapper;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.tts.service.TtsAudioResult;
import com.interview.module.tts.service.TtsRenderOptions;
import com.interview.module.tts.service.TtsService;

class SimpleInterviewEngineIntegrationTest {

	@Test
	void should_surface_stage_and_duration_in_session_view() {
		var engine = defaultEngine();
		var view = engine.startSession(List.of(), 120, 3, new InterviewSessionOwner("1", "tester"), null, null);

		assertThat(view.stage()).isEqualTo("OPENING");
		assertThat(view.durationMinutes()).isEqualTo(120);
		assertThat(view.maxFollowUpPerQuestion()).isEqualTo(3);
	}

	@Test
	void should_keep_question_source_and_difficulty_in_snapshot() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewQuestionCard question = new InterviewQuestionCard(
				"Strategy question",
				"Explain how you would handle scaling.",
				"IMPORT",
				"Q-123",
				"CAT-456",
				5
		);

		var view = engine.startSession(
				List.of(question),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);
		InterviewQuestionSnapshot viewQuestion = view.questions().get(0);
		InterviewSessionState storedSession = sessionStore.findById(view.sessionId()).orElseThrow();
		InterviewQuestionSnapshot storedQuestion = storedSession.getQuestions().get(0);

		assertThat(viewQuestion.sourceSnapshot()).isEqualTo("IMPORT");
		assertThat(viewQuestion.difficultySnapshot()).isEqualTo(5);
		assertThat(storedQuestion.sourceSnapshot()).isEqualTo("IMPORT");
		assertThat(storedQuestion.difficultySnapshot()).isEqualTo(5);
	}

	@Test
	void should_normalize_snapshot_defaults_in_constructor_and_start_session_view() {
		InterviewQuestionSnapshot directSnapshot = new InterviewQuestionSnapshot(
				1,
				"Strategy question",
				"Explain how you would handle scaling."
		);

		assertThat(directSnapshot.sourceSnapshot()).isEqualTo("PRESET");
		assertThat(directSnapshot.difficultySnapshot()).isEqualTo(1);

		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(
						new InterviewQuestionCard(
								"Normalized question",
								"Explain eventual consistency.",
								"   ",
								"Q-100",
								"CAT-100",
								null
						),
						new InterviewQuestionCard(
								"Null-source question",
								"Explain idempotency.",
								null,
								"Q-101",
								"CAT-101",
								null
						)
				),
				30,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		List<InterviewQuestionSnapshot> viewQuestions = view.questions();
		List<InterviewQuestionSnapshot> storedQuestions = sessionStore.findById(view.sessionId()).orElseThrow().getQuestions();

		assertThat(viewQuestions).extracting(InterviewQuestionSnapshot::sourceSnapshot)
				.containsExactly("PRESET", "PRESET");
		assertThat(viewQuestions).extracting(InterviewQuestionSnapshot::difficultySnapshot)
				.containsExactly(1, 1);
		assertThat(storedQuestions).extracting(InterviewQuestionSnapshot::sourceSnapshot)
				.containsExactly("PRESET", "PRESET");
		assertThat(storedQuestions).extracting(InterviewQuestionSnapshot::difficultySnapshot)
				.containsExactly(1, 1);
	}

	@Test
	void should_default_missing_question_metadata_to_preset_when_persisting_jdbc_snapshots() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		SessionQuestionMapper sessionQuestionMapper = mock(SessionQuestionMapper.class);
		RoundMapper roundMapper = mock(RoundMapper.class);
		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(42L);

		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(sessionMapper.selectById(42L)).thenReturn(sessionEntity);

		JdbcInterviewSessionStore store = new JdbcInterviewSessionStore(
				sessionMapper,
				sessionQuestionMapper,
				roundMapper,
				new ObjectMapper(),
				60
		);
		InterviewSessionState sessionState = new InterviewSessionState(
				"session-1",
				"1",
				"tester",
				List.of(
						new InterviewQuestionSnapshot(
								1,
								"Strategy question",
								"Explain how you would handle scaling.",
								"  ",
								null
						),
						new InterviewQuestionSnapshot(
								2,
								"Null source question",
								"Explain idempotency.",
								null,
								null
						)
				),
				"OPENING",
				60,
				2,
				null,
				1.0
		);

		store.save(sessionState);

		ArgumentCaptor<SessionQuestionEntity> questionCaptor = ArgumentCaptor.forClass(SessionQuestionEntity.class);
		verify(sessionQuestionMapper, times(2)).insert(questionCaptor.capture());
		List<SessionQuestionEntity> storedQuestions = questionCaptor.getAllValues();

		assertThat(storedQuestions).extracting(SessionQuestionEntity::getSourceSnapshot)
				.containsExactly("MANUAL", "MANUAL");
		assertThat(storedQuestions).extracting(SessionQuestionEntity::getDifficultySnapshot)
				.containsExactly(1, 1);
	}

	@Test
	void should_not_force_follow_up_for_non_blank_self_introduction() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("自我介绍", "请做一个简短的自我介绍。")),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(view.sessionId(), "1", "TEXT", "我有五年 Java 后端开发经验，最近主要负责交易系统。", null);

		assertThat(answered.status()).isEqualTo("COMPLETED");
		assertThat(answered.followUpIndex()).isZero();
	}

	@Test
	void should_follow_up_when_missing_key_points_and_record_decision_metadata() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);

		assertThat(answered.status()).isEqualTo("IN_PROGRESS");
		assertThat(answered.followUpIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(0).followUpDecision()).isEqualTo("FOLLOW_UP");
		assertThat(answered.rounds().get(0).followUpDecisionReason()).contains("缺少关键点");
		assertThat(answered.rounds().get(0).missingPointsSnapshot()).contains("一致性策略");
	}

	@Test
	void should_stop_following_up_after_policy_limit_for_normal_questions() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService(),
				new FollowUpDecisionEngine(new InterviewFlowPolicy(60, 120, 1, 2, 1, 0))
		);
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "我们用 Redis 做缓存。", null);
		var second = engine.answer(view.sessionId(), "1", "TEXT", "还是主要做缓存。", null);

		assertThat(second.followUpIndex()).isZero();
		assertThat(second.currentQuestionIndex()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void should_allow_follow_up_on_last_question_when_answer_is_incomplete() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(
						new InterviewQuestionCard("自我介绍", "请做一个简短的自我介绍。"),
						new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)
				),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var first = engine.answer(view.sessionId(), "1", "TEXT", "我有五年 Java 后端开发经验。", null);
		var second = engine.answer(first.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);

		assertThat(second.status()).isEqualTo("IN_PROGRESS");
		assertThat(second.followUpIndex()).isEqualTo(1);
		assertThat(second.rounds().get(2).roundType()).isEqualTo("FOLLOW_UP");
	}

	@Test
	void should_use_off_topic_follow_up_text_for_clearly_wrong_answer() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("消息队列", "请说明你们系统是否用了消息队列。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(view.sessionId(), "1", "TEXT", "我们主要通过乐观锁解决并发更新。", null);

		assertThat(answered.rounds().get(1).aiMessageText()).contains("回到题目本身");
	}

	@Test
	void should_pass_question_context_into_ai_service_command() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		RecordingAiService aiService = new RecordingAiService();
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				aiService,
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(new InterviewQuestionCard("并发控制", "请说明你在订单系统中如何处理并发更新。")),
				45,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);
		engine.answer(view.sessionId(), "1", "TEXT", "我会用乐观锁。", null);

		assertThat(aiService.lastCommand).isNotNull();
		assertThat(aiService.lastCommand.question()).contains("并发更新");
		assertThat(aiService.lastCommand.answer()).contains("我会用乐观锁");
		assertThat(aiService.lastCommand.stage()).isEqualTo("OPENING");
		assertThat(aiService.lastCommand.followUpIndex()).isZero();
		assertThat(aiService.lastCommand.maxFollowUpPerQuestion()).isEqualTo(2);
		assertThat(aiService.lastCommand.expectedPoints()).contains("并发控制");
	}

	@Test
	void should_pass_blank_answer_to_analyzer_as_unanswered() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		RecordingAiService aiService = new RecordingAiService();
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				aiService,
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(new InterviewQuestionCard("缓存设计", "请说明 Redis 的使用场景和一致性策略。")),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "   ", null);

		assertThat(aiService.lastAnalysis).isNotNull();
		assertThat(aiService.lastAnalysis.answered()).isFalse();
		assertThat(aiService.lastAnalysis.reasonCodes()).contains("ANSWER_EMPTY");
	}

	@Test
	void should_include_rule_explanations_in_report_view() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
	}

	@Test
	void should_prioritize_risk_explanation_over_missing_points_in_report_view() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("消息队列", "请说明消息队列削峰和解耦的区别。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "我们主要通过乐观锁解决并发更新。", null);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation().summaryText()).contains("答偏风险");
		assertThat(report.questionReports().get(0).explanation().summaryText()).doesNotContain("缺少对");
	}

	@Test
	void should_mark_unanswered_question_as_insufficient_data_in_report_view() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("缓存设计", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).score()).isNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation().performanceLevel()).isNull();
		assertThat(report.questionReports().get(0).explanation().summaryText()).containsAnyOf("未作答", "数据不足", "尚未形成有效评分");
		assertThat(report.questionReports().get(0).explanation().summaryText()).doesNotContain("基础回答有了");
	}

	@Test
	void should_mark_unanswered_session_overall_explanation_as_no_data() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("缓存设计", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.overallExplanation().level()).isNull();
		assertThat(report.overallExplanation().summaryText()).containsAnyOf("有效答题记录不足", "尚未形成完整诊断", "数据不足");
		assertThat(report.overallExplanation().summaryText()).doesNotContain("整体基础可用");
	}

	@Test
	void should_keep_score_on_answered_round_instead_of_next_question_opening_round() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new SequencedAiService(List.of(84)),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(
						new InterviewQuestionCard("自我介绍", "请做一个简短的自我介绍。"),
						new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)
				),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(
				view.sessionId(),
				"1",
				"TEXT",
				"我有五年 Java 后端开发经验，最近主要负责交易系统。",
				null
		);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(answered.rounds()).hasSize(2);
		assertThat(answered.rounds().get(0).questionIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(0).scoreSuggestion()).isEqualTo(84);
		assertThat(answered.rounds().get(1).questionIndex()).isEqualTo(2);
		assertThat(answered.rounds().get(1).scoreSuggestion()).isNull();
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::score)
				.containsExactly(84, null);
	}

	@Test
	void should_keep_score_on_answered_round_when_last_question_naturally_completes() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new SequencedAiService(List.of(86)),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(new InterviewQuestionCard("自我介绍", "请做一个简短的自我介绍。")),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(
				view.sessionId(),
				"1",
				"TEXT",
				"我有五年 Java 后端开发经验，最近主要负责交易系统。",
				null
		);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(answered.status()).isEqualTo("COMPLETED");
		assertThat(answered.rounds()).hasSize(2);
		assertThat(answered.rounds().get(0).roundType()).isEqualTo("QUESTION");
		assertThat(answered.rounds().get(0).questionIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(0).scoreSuggestion()).isEqualTo(86);
		assertThat(answered.rounds().get(1).roundType()).isEqualTo("END_INTERVIEW");
		assertThat(answered.rounds().get(1).questionIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(1).scoreSuggestion()).isNull();
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::score)
				.containsExactly(86);
	}

	@Test
	void should_keep_score_on_answered_round_when_decision_forces_end_interview() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		FollowUpDecisionEngine forcedEndDecisionEngine = new FollowUpDecisionEngine(new InterviewFlowPolicy(60, 120, 1, 2, 1, 0)) {
			@Override
			public FollowUpDecision decide(
					InterviewQuestionSnapshot question,
					String stage,
					int followUpIndex,
					int sessionMaxFollowUp,
					AnswerEvidence evidence
			) {
				return FollowUpDecision.endInterview("FORCED_END", "达到收尾条件");
			}
		};
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new SequencedAiService(List.of(77)),
				new StubTtsService(),
				forcedEndDecisionEngine
		);

		var view = engine.startSession(
				List.of(
						new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。"),
						new InterviewQuestionCard("消息队列", "请说明消息队列削峰和解耦的区别。")
				),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(
				view.sessionId(),
				"1",
				"TEXT",
				"Redis 主要用于热点缓存，一致性上我会先更新数据库再删除缓存。",
				null
		);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(answered.status()).isEqualTo("COMPLETED");
		assertThat(answered.rounds()).hasSize(2);
		assertThat(answered.rounds().get(0).roundType()).isEqualTo("QUESTION");
		assertThat(answered.rounds().get(0).questionIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(0).scoreSuggestion()).isEqualTo(77);
		assertThat(answered.rounds().get(1).roundType()).isEqualTo("END_INTERVIEW");
		assertThat(answered.rounds().get(1).questionIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(1).scoreSuggestion()).isNull();
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::score)
				.containsExactly(77, null);
	}

	@Test
	void should_drop_early_missing_points_after_follow_up_completion_in_final_report() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new SequencedAiService(List.of(60, 88)),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var followUp = engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);
		engine.answer(
				followUp.sessionId(),
				"1",
				"TEXT",
				"Redis 的使用场景主要是热点缓存，因为读多写少能降低数据库压力。涉及一致性时，我会先更新数据库，然后删除缓存，最后通过订阅 binlog 异步校正，权衡点是短暂不一致但吞吐更高。",
				null
		);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).score()).isEqualTo(88);
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation().summaryText()).doesNotContain("还缺少对");
		assertThat(report.questionReports().get(0).explanation().summaryText()).contains("回答较完整");
		assertThat(report.questionReports().get(0).explanation().evidencePoints())
				.noneMatch(item -> item.contains("缺少关键点"));
	}

	@Test
	void should_align_overall_score_with_final_question_score_after_follow_up_recovery() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new SequencedAiService(List.of(60, 88)),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);
		var followUp = engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);
		engine.answer(
				followUp.sessionId(),
				"1",
				"TEXT",
				"Redis 的使用场景主要是热点缓存，一致性时我会先更新数据库再删除缓存，并用 binlog 异步校正。",
				null
		);

		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).score()).isEqualTo(88);
		assertThat(report.overallScore()).isEqualTo(88);
		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.overallExplanation().level()).isEqualTo("STRONG");
	}

	@Test
	void should_backfill_legacy_persisted_report_and_upgrade_to_v2() {
		String sessionId = "session-legacy-backfill";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		InterviewReportView legacyReport = legacyPersistedReport(sessionId);
		reportStore.seed(new PersistedInterviewReport(legacyReport, "v1"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report.sessionId()).isEqualTo(legacyReport.sessionId());
		assertThat(report.status()).isEqualTo(legacyReport.status());
		assertThat(report.title()).isEqualTo(legacyReport.title());
		assertThat(report.overallScore()).isEqualTo(legacyReport.overallScore());
		assertThat(report.overallComment()).isEqualTo(legacyReport.overallComment());
		assertThat(report.strengths()).containsExactlyElementsOf(legacyReport.strengths());
		assertThat(report.weaknesses()).containsExactlyElementsOf(legacyReport.weaknesses());
		assertThat(report.suggestions()).containsExactlyElementsOf(legacyReport.suggestions());
		assertThat(report.questionReports()).hasSize(legacyReport.questionReports().size());
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::title)
				.containsExactlyElementsOf(legacyReport.questionReports().stream().map(InterviewQuestionReportView::title).toList());
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::prompt)
				.containsExactlyElementsOf(legacyReport.questionReports().stream().map(InterviewQuestionReportView::prompt).toList());
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::score)
				.containsExactlyElementsOf(legacyReport.questionReports().stream().map(InterviewQuestionReportView::score).toList());
		assertThat(report.questionReports()).extracting(InterviewQuestionReportView::summary)
				.containsExactlyElementsOf(legacyReport.questionReports().stream().map(InterviewQuestionReportView::summary).toList());
		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveAttempts()).isEqualTo(1);
		assertThat(reportStore.lastSavedVersion()).isEqualTo("v2");
	}

	@Test
	void should_backfill_missing_explanations_even_when_persisted_report_is_v2() {
		String sessionId = "session-v2-missing-explanations";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		reportStore.seed(new PersistedInterviewReport(legacyPersistedReport(sessionId), "v2"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveAttempts()).isEqualTo(1);
		assertThat(reportStore.lastSavedVersion()).isEqualTo("v2");
	}

	@Test
	void should_skip_backfill_when_persisted_report_already_has_explanations() {
		String sessionId = "session-already-backfilled";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		InterviewReportView reportWithExplanation = backfilledPersistedReport(sessionId);
		reportStore.seed(new PersistedInterviewReport(reportWithExplanation, "v2"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report).isSameAs(reportWithExplanation);
		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveAttempts()).isZero();
	}

	@Test
	void should_return_old_persisted_report_when_backfill_save_fails() {
		String sessionId = "session-save-fail";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		InterviewReportView legacyReport = legacyPersistedReport(sessionId);
		reportStore.seed(new PersistedInterviewReport(legacyReport, "v1"));
		reportStore.setThrowOnSave(true);
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report).isEqualTo(legacyReport);
		assertThat(reportStore.saveAttempts()).isEqualTo(1);
	}

	@Test
	void should_return_old_persisted_report_when_session_context_is_incomplete() {
		String sessionId = "session-context-incomplete";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		InterviewSessionState incompleteSession = new InterviewSessionState(
				sessionId,
				"1",
				"tester",
				List.of(),
				"WRAP_UP",
				60,
				2,
				null,
				1.0
		);
		incompleteSession.setStatus("COMPLETED");
		sessionStore.save(incompleteSession);
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		InterviewReportView legacyReport = legacyPersistedReport(sessionId);
		reportStore.seed(new PersistedInterviewReport(legacyReport, "v1"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report).isEqualTo(legacyReport);
		assertThat(reportStore.saveAttempts()).isZero();
	}

	@Test
	void should_not_call_polish_interview_report_explanation_for_historical_backfill() {
		String sessionId = "session-no-polish-during-backfill";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		reportStore.seed(new PersistedInterviewReport(legacyPersistedReport(sessionId), "v1"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new ThrowingPolishAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveAttempts()).isEqualTo(1);
	}

	@Test
	void should_rebuild_report_when_persisted_report_payload_is_null() {
		String sessionId = "session-null-persisted-payload";
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession(sessionId));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore();
		reportStore.seedForSession(sessionId, new PersistedInterviewReport(null, "v1"));
		SimpleInterviewEngine engine = engineWithStores(sessionStore, reportStore, new StubAiService());

		InterviewReportView report = engine.getReport(sessionId, "1");

		assertThat(report).isNotNull();
		assertThat(report.sessionId()).isEqualTo(sessionId);
		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports()).isNotEmpty();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveAttempts()).isEqualTo(1);
		assertThat(reportStore.lastSavedVersion()).isEqualTo("v2");
	}

	private SimpleInterviewEngine defaultEngine() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		return new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);
	}

	private SimpleInterviewEngine engineWithStores(
			InterviewSessionStore sessionStore,
			InterviewReportStore reportStore,
			AiService aiService
	) {
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		return new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				aiService,
				new StubTtsService(),
				defaultDecisionEngine()
		);
	}

	private static InterviewSessionState completedRedisSession(String sessionId) {
		InterviewSessionState session = new InterviewSessionState(
				sessionId,
				"1",
				"tester",
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。")),
				"WRAP_UP",
				60,
				2,
				null,
				1.0
		);
		session.setStatus("COMPLETED");
		session.getRounds().add(new InterviewRoundRecord(
				"round-1",
				1,
				0,
				"QUESTION",
				"请说明 Redis 的使用场景和一致性策略。",
				"/audio/tts-round-1",
				1000L,
				70,
				"Redis 可以做缓存。",
				null,
				"TEXT",
				"2026-01-01T10:00:00Z",
				"2026-01-01T10:00:15Z",
				"缺少关键点：一致性策略",
				"FOLLOW_UP",
				"缺少关键点",
				List.of("一致性策略")
		));
		return session;
	}

	private static InterviewReportView legacyPersistedReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Redis",
				70,
				"基础可用，但关键细节仍需补强。",
				List.of("有基础回答结构"),
				List.of("一致性策略说明不足"),
				List.of("补充数据库与缓存一致性方案"),
				List.of(new InterviewQuestionReportView(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						70,
						"核心点覆盖不完整。",
						null
				)),
				null
		, null);
	}

	private static InterviewReportView backfilledPersistedReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Redis",
				70,
				"基础可用，但关键细节仍需补强。",
				List.of("有基础回答结构"),
				List.of("一致性策略说明不足"),
				List.of("补充数据库与缓存一致性方案"),
				List.of(new InterviewQuestionReportView(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						70,
						"核心点覆盖不完整。",
						new InterviewQuestionExplanationView(
								"MEDIUM",
								"Redis 这题基础回答有了，但细节深度和案例支撑还不够充分。",
								List.of("缺少关键点：一致性策略"),
								"补充更具体的案例和取舍说明，让回答从结论走到可落地方案。",
								"RULE"
						)
				)),
				new InterviewOverallExplanationView(
						"MEDIUM",
						"整体基础可用，但关键点覆盖和追问深度还不够稳定，部分题目仍需要继续补强。",
						List.of("共有 1 个答题轮次暴露出关键点缺失，回答覆盖度还不够稳定。"),
						List.of("按题型整理每题必须覆盖的关键点，先保证回答完整度。"),
						"RULE"
				)
		, null);
	}

	private static FollowUpDecisionEngine defaultDecisionEngine() {
		return new FollowUpDecisionEngine(new InterviewFlowPolicy(60, 120, 1, 2, 1, 0));
	}

	private static final class InMemorySessionStore implements InterviewSessionStore {
		private final Map<String, InterviewSessionState> sessions = new HashMap<>();

		@Override
		public void save(InterviewSessionState sessionState) {
			sessions.put(sessionState.getSessionId(), sessionState);
		}

		@Override
		public Optional<InterviewSessionState> findById(String sessionId) {
			return Optional.ofNullable(sessions.get(sessionId));
		}

		@Override
		public List<InterviewSessionState> findAll() {
			return List.copyOf(sessions.values());
		}
	}

	private static final class StubAiService implements AiService {
		@Override
		public AiReply generateInterviewReply(InterviewReplyCommand command) {
			return new AiReply("继续", "NEXT_QUESTION", 80);
		}

		@Override
		public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
			return new ResumeKeywordExtractionResult("summary", List.of(), List.of());
		}

		@Override
		public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
			return List.of();
		}

		@Override
		public AnswerEvidence analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
			return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
		}
	}

	private static final class RecordingAiService implements AiService {
		private InterviewReplyCommand lastCommand;
		private AnswerEvidence lastAnalysis;

		@Override
		public AiReply generateInterviewReply(InterviewReplyCommand command) {
			this.lastCommand = command;
			return new AiReply("继续", "NEXT_QUESTION", 80);
		}

		@Override
		public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
			return new ResumeKeywordExtractionResult("summary", List.of(), List.of());
		}

		@Override
		public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
			return List.of();
		}

		@Override
		public AnswerEvidence analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
			this.lastAnalysis = InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
			return this.lastAnalysis;
		}
	}

	private static final class SequencedAiService implements AiService {
		private final List<Integer> scores;
		private int replyIndex = 0;

		private SequencedAiService(List<Integer> scores) {
			this.scores = List.copyOf(scores);
		}

		@Override
		public AiReply generateInterviewReply(InterviewReplyCommand command) {
			int score = scores.get(Math.min(replyIndex, scores.size() - 1));
			replyIndex++;
			return new AiReply("继续", "NEXT_QUESTION", score);
		}

		@Override
		public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
			return new ResumeKeywordExtractionResult("summary", List.of(), List.of());
		}

		@Override
		public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
			return List.of();
		}

		@Override
		public AnswerEvidence analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
			return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
		}
	}

	private static final class ThrowingPolishAiService implements AiService {
		@Override
		public AiReply generateInterviewReply(InterviewReplyCommand command) {
			return new AiReply("继续", "NEXT_QUESTION", 80);
		}

		@Override
		public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
			return new ResumeKeywordExtractionResult("summary", List.of(), List.of());
		}

		@Override
		public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
			return List.of();
		}

		@Override
		public InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
			throw new AssertionError("polishInterviewReportExplanation should not be called for historical backfill");
		}
	}

	private static final class RecordingPersistedReportStore implements InterviewReportStore {
		private final Map<String, PersistedInterviewReport> store = new HashMap<>();
		private int saveAttempts;
		private String lastSavedVersion;
		private boolean throwOnSave;

		@Override
		public Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId) {
			return Optional.ofNullable(store.get(sessionId));
		}

		@Override
		public void save(InterviewReportView report, String reportVersion) {
			saveAttempts++;
			lastSavedVersion = reportVersion;
			if (throwOnSave) {
				throw new IllegalStateException("save failed");
			}
			store.put(report.sessionId(), new PersistedInterviewReport(report, reportVersion));
		}

		private void seed(PersistedInterviewReport persistedReport) {
			store.put(persistedReport.report().sessionId(), persistedReport);
		}

		private void seedForSession(String sessionId, PersistedInterviewReport persistedReport) {
			store.put(sessionId, persistedReport);
		}

		private void setThrowOnSave(boolean throwOnSave) {
			this.throwOnSave = throwOnSave;
		}

		private int saveAttempts() {
			return saveAttempts;
		}

		private String lastSavedVersion() {
			return lastSavedVersion;
		}
	}

	private static final class StubTtsService implements TtsService {
		@Override
		public TtsAudioResult synthesize(String text, TtsRenderOptions options) {
			return new TtsAudioResult("tts-file", "/audio/tts-file", 1000L);
		}
	}
}
