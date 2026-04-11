package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.interview.engine.SimpleInterviewEngine;
import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.store.InterviewReportStore;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;
import com.interview.module.interview.engine.store.NoopInterviewReportStore;
import com.interview.module.interview.service.InterviewAnswerAnalyzer.Analysis;
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
				new StubTtsService()
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
		InterviewSessionState storedSession = sessionStore.findById(view.sessionId()).orElseThrow();
		InterviewQuestionSnapshot storedQuestion = storedSession.getQuestions().get(0);

		assertThat(storedQuestion.sourceSnapshot()).isEqualTo("IMPORT");
		assertThat(storedQuestion.difficultySnapshot()).isEqualTo(5);
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
				new StubTtsService()
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

	private SimpleInterviewEngine defaultEngine() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		return new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService()
		);
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
		public Analysis analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
			return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
		}
	}

	private static final class RecordingAiService implements AiService {
		private InterviewReplyCommand lastCommand;

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
		public Analysis analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
			return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
		}
	}

	private static final class StubTtsService implements TtsService {
		@Override
		public TtsAudioResult synthesize(String text, TtsRenderOptions options) {
			return new TtsAudioResult("tts-file", "/audio/tts-file", 1000L);
		}
	}
}
