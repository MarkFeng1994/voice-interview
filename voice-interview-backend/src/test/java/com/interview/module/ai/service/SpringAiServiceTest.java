package com.interview.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import com.interview.module.ai.service.springai.SpringAiService;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;

class SpringAiServiceTest {

	private ChatModel chatModel;
	private SpringAiService springAiService;
	private ProviderMetricsService providerMetricsService;

	@BeforeEach
	void setUp() {
		chatModel = mock(ChatModel.class);
		providerMetricsService = new ProviderMetricsService();
		ChatClient chatClient = ChatClient.create(chatModel);
		springAiService = new SpringAiService(chatClient, providerMetricsService);
	}

	private ChatResponse chatResponseWithJson(String json) {
		AssistantMessage message = new AssistantMessage(json);
		return new ChatResponse(List.of(new Generation(message)));
	}

	@Nested
	@DisplayName("generateInterviewReply")
	class GenerateInterviewReply {

		@Test
		@DisplayName("normal reply with all fields")
		void normalReply() {
			String json = """
					{"spokenText":"不错的回答，我们继续深入。","decisionSuggestion":"FOLLOW_UP","scoreSuggestion":78}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			InterviewReplyCommand command = new InterviewReplyCommand(
					"Java 线程池", "ThreadPoolExecutor", "ANSWER", 0, 2, List.of("核心线程", "拒绝策略")
			);
			AiReply reply = springAiService.generateInterviewReply(command);

			assertThat(reply.spokenText()).isEqualTo("不错的回答，我们继续深入。");
			assertThat(reply.decisionSuggestion()).isEqualTo("FOLLOW_UP");
			assertThat(reply.scoreSuggestion()).isEqualTo(78);
		}

		@Test
		@DisplayName("fallback when spokenText is blank")
		void fallbackWhenSpokenTextBlank() {
			String json = """
					{"spokenText":"","decisionSuggestion":"NEXT_QUESTION","scoreSuggestion":60}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			AiReply reply = springAiService.generateInterviewReply(
					new InterviewReplyCommand("Q", "A", "ANSWER", 0, 2, List.of())
			);

			assertThat(reply.spokenText()).isEqualTo("好的，我们继续。");
			assertThat(reply.decisionSuggestion()).isEqualTo("NEXT_QUESTION");
		}

		@Test
		@DisplayName("fallback when decisionSuggestion is null")
		void fallbackWhenDecisionNull() {
			String json = """
					{"spokenText":"继续","decisionSuggestion":null,"scoreSuggestion":50}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			AiReply reply = springAiService.generateInterviewReply(
					new InterviewReplyCommand("Q", "A", "ANSWER", 0, 2, List.of())
			);

			assertThat(reply.decisionSuggestion()).isEqualTo("FOLLOW_UP");
		}

		@Test
		@DisplayName("records metrics on success")
		void recordsMetrics() {
			String json = """
					{"spokenText":"test","decisionSuggestion":"FOLLOW_UP","scoreSuggestion":70}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			springAiService.generateInterviewReply(
					new InterviewReplyCommand("Q", "A", "ANSWER", 0, 2, List.of())
			);

			var snapshot = providerMetricsService.snapshot();
			assertThat(snapshot).hasSize(1);
			assertThat(snapshot.get(0).provider()).isEqualTo("springai");
			assertThat(snapshot.get(0).successCalls()).isEqualTo(1);
		}

		@Test
		@DisplayName("records metrics on failure")
		void recordsMetricsOnFailure() {
			when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

			assertThatThrownBy(() -> springAiService.generateInterviewReply(
					new InterviewReplyCommand("Q", "A", "ANSWER", 0, 2, List.of())
			)).isInstanceOf(RuntimeException.class);

			var snapshot = providerMetricsService.snapshot();
			assertThat(snapshot).hasSize(1);
			assertThat(snapshot.get(0).failureCalls()).isEqualTo(1);
		}

		@Test
		@DisplayName("handles null command gracefully")
		void nullCommand() {
			String json = """
					{"spokenText":"请开始回答","decisionSuggestion":"FOLLOW_UP","scoreSuggestion":0}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			AiReply reply = springAiService.generateInterviewReply(null);

			assertThat(reply.spokenText()).isEqualTo("请开始回答");
		}
	}

	@Nested
	@DisplayName("extractResumeKeywords")
	class ExtractResumeKeywords {

		@Test
		@DisplayName("normal extraction")
		void normalExtraction() {
			String json = """
					{"summary":"5年Java经验","keywords":["Java","Spring","微服务"],"experienceHighlights":["高并发项目","分布式系统"]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			ResumeKeywordExtractionResult result = springAiService.extractResumeKeywords("简历内容...");

			assertThat(result.summary()).isEqualTo("5年Java经验");
			assertThat(result.keywords()).containsExactly("Java", "Spring", "微服务");
			assertThat(result.experienceHighlights()).containsExactly("高并发项目", "分布式系统");
		}

		@Test
		@DisplayName("fallback when output fields are null")
		void fallbackWhenNullFields() {
			String json = """
					{"summary":null,"keywords":null,"experienceHighlights":null}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			ResumeKeywordExtractionResult result = springAiService.extractResumeKeywords("简历");

			assertThat(result.summary()).isEqualTo("候选人具备技术开发经验");
			assertThat(result.keywords()).isEmpty();
			assertThat(result.experienceHighlights()).isEmpty();
		}

		@Test
		@DisplayName("filters null/blank entries from lists")
		void filtersNullBlankEntries() {
			String json = """
					{"summary":"summary","keywords":["Java","  ",null,"Spring"],"experienceHighlights":null}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			ResumeKeywordExtractionResult result = springAiService.extractResumeKeywords("简历");

			assertThat(result.keywords()).containsExactly("Java", "Spring");
			assertThat(result.experienceHighlights()).isEmpty();
		}
	}

	@Nested
	@DisplayName("generateResumeQuestions")
	class GenerateResumeQuestions {

		@Test
		@DisplayName("normal generation")
		void normalGeneration() {
			String json = """
					{"questions":[{"title":"微服务架构","prompt":"请描述微服务拆分原则","targetKeyword":"微服务","difficulty":3}]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			ResumeQuestionGenerationCommand command = new ResumeQuestionGenerationCommand(
					"5年Java经验", List.of("微服务"), List.of(), List.of(), 3
			);
			List<GeneratedResumeQuestion> questions = springAiService.generateResumeQuestions(command);

			assertThat(questions).hasSize(1);
			assertThat(questions.get(0).title()).isEqualTo("微服务架构");
			assertThat(questions.get(0).difficulty()).isEqualTo(3);
		}

		@Test
		@DisplayName("returns empty when questionCount <= 0")
		void emptyWhenZeroCount() {
			ResumeQuestionGenerationCommand command = new ResumeQuestionGenerationCommand(
					"summary", List.of(), List.of(), List.of(), 0
			);
			List<GeneratedResumeQuestion> questions = springAiService.generateResumeQuestions(command);

			assertThat(questions).isEmpty();
			verify(chatModel, org.mockito.Mockito.never()).call(any(Prompt.class));
		}

		@Test
		@DisplayName("returns empty when command is null")
		void emptyWhenNullCommand() {
			List<GeneratedResumeQuestion> questions = springAiService.generateResumeQuestions(null);
			assertThat(questions).isEmpty();
		}

		@Test
		@DisplayName("limits questions to requested count")
		void limitsToRequestedCount() {
			String json = """
					{"questions":[
						{"title":"Q1","prompt":"P1","targetKeyword":"K1","difficulty":2},
						{"title":"Q2","prompt":"P2","targetKeyword":"K2","difficulty":3},
						{"title":"Q3","prompt":"P3","targetKeyword":"K3","difficulty":1}
					]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			ResumeQuestionGenerationCommand command = new ResumeQuestionGenerationCommand(
					"summary", List.of("K"), List.of(), List.of(), 2
			);
			List<GeneratedResumeQuestion> questions = springAiService.generateResumeQuestions(command);

			assertThat(questions).hasSize(2);
		}
	}

	@Nested
	@DisplayName("polishInterviewReportExplanation")
	class PolishReportExplanation {

		@Test
		@DisplayName("normal polish with slot markers preserved")
		void normalPolish() {
			String json = """
					{"summaryText":"[SUMMARY:OVERALL:MEDIUM] 整体表现中等","evidencePoints":["[E1] 回答基本正确"],"improvementSuggestions":["[S1] 需加强并发知识"]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			InterviewReportExplanationCommand command = new InterviewReportExplanationCommand(
					"overall", "总体评价", "请润色", "MEDIUM",
					"[SUMMARY:OVERALL:MEDIUM] 表现中等",
					List.of("[E1] 回答正确"),
					List.of("[S1] 需加强")
			);
			InterviewReportExplanationResult result = springAiService.polishInterviewReportExplanation(command);

			assertThat(result.summaryText()).startsWith("[SUMMARY:OVERALL:MEDIUM]");
			assertThat(result.evidencePoints()).hasSize(1);
		}

		@Test
		@DisplayName("throws when summaryText slot marker is missing")
		void throwsWhenSlotMarkerMissing() {
			String json = """
					{"summaryText":"Missing slot marker","evidencePoints":[],"improvementSuggestions":[]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			InterviewReportExplanationCommand command = new InterviewReportExplanationCommand(
					"overall", "title", "prompt", "MEDIUM",
					"[SUMMARY:OVERALL:MEDIUM] original",
					List.of(), List.of()
			);

			assertThatThrownBy(() -> springAiService.polishInterviewReportExplanation(command))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("slot marker is missing");
		}

		@Test
		@DisplayName("returns null when command is null")
		void nullCommand() {
			InterviewReportExplanationResult result = springAiService.polishInterviewReportExplanation(null);
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("filters blank entries from lists")
		void filtersBlankEntries() {
			String json = """
					{"summaryText":"[SUMMARY:QUESTION:WEAK] weak answer","evidencePoints":["[E1] valid","  ",null],"improvementSuggestions":["[S1] valid"]}
					""";
			when(chatModel.call(any(Prompt.class))).thenReturn(chatResponseWithJson(json));

			InterviewReportExplanationCommand command = new InterviewReportExplanationCommand(
					"question", "title", "prompt", "WEAK",
					"[SUMMARY:QUESTION:WEAK] original",
					List.of(), List.of()
			);
			InterviewReportExplanationResult result = springAiService.polishInterviewReportExplanation(command);

			assertThat(result.evidencePoints()).containsExactly("[E1] valid");
		}
	}
}
