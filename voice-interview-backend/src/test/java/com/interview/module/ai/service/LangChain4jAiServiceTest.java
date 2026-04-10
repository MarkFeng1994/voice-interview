package com.interview.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.ai.service.langchain4j.InterviewReplyAssistant;
import com.interview.module.ai.service.langchain4j.LangChain4jAiService;
import com.interview.module.ai.service.langchain4j.LangChain4jAssistantFactory;
import com.interview.module.ai.service.langchain4j.ResumeKeywordAssistant;
import com.interview.module.ai.service.langchain4j.ResumeQuestionAssistant;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;
import com.interview.module.system.service.ProviderMetricsService.ProviderMetricView;

class LangChain4jAiServiceTest {

	@Test
	void should_map_interview_reply_json_and_record_metrics() {
		InterviewReplyAssistant assistant = mock(InterviewReplyAssistant.class);
		when(assistant.generate(
				"请介绍你在上一家公司最有挑战的一次故障排查。",
				"我先定位日志，再做链路回放。",
				"FOLLOW_UP",
				1,
				2,
				List.of("排查思路", "根因定位")
		)).thenReturn("""
				```json
				{
				  "spokenText": "可以，继续展开说根因。",
				  "decisionSuggestion": "FOLLOW_UP",
				  "scoreSuggestion": 86
				}
				```""");

		ProviderMetricsService metricsService = new ProviderMetricsService();
		LangChain4jAiService service = createService(metricsService, assistant, mock(ResumeKeywordAssistant.class), mock(ResumeQuestionAssistant.class));

		AiReply reply = service.generateInterviewReply(new InterviewReplyCommand(
				"请介绍你在上一家公司最有挑战的一次故障排查。",
				"我先定位日志，再做链路回放。",
				"FOLLOW_UP",
				1,
				2,
				List.of("排查思路", "根因定位")
		));

		assertThat(reply.spokenText()).isEqualTo("可以，继续展开说根因。");
		assertThat(reply.decisionSuggestion()).isEqualTo("FOLLOW_UP");
		assertThat(reply.scoreSuggestion()).isEqualTo(86);
		ProviderMetricView metric = findMetric(metricsService, "AI");
		assertThat(metric.provider()).isEqualTo("langchain4j");
		assertThat(metric.totalCalls()).isEqualTo(1);
		assertThat(metric.successCalls()).isEqualTo(1);
		assertThat(metric.failureCalls()).isEqualTo(0);
		verify(assistant).generate(
				"请介绍你在上一家公司最有挑战的一次故障排查。",
				"我先定位日志，再做链路回放。",
				"FOLLOW_UP",
				1,
				2,
				List.of("排查思路", "根因定位")
		);
	}

	@Test
	void should_fallback_when_interview_reply_json_is_invalid() {
		InterviewReplyAssistant assistant = mock(InterviewReplyAssistant.class);
		when(assistant.generate(
				"请介绍一次线上问题复盘。",
				"我排查了日志。",
				"FOLLOW_UP",
				0,
				2,
				List.of("定位过程")
		)).thenReturn("这不是 JSON");

		ProviderMetricsService metricsService = new ProviderMetricsService();
		LangChain4jAiService service = createService(metricsService, assistant, mock(ResumeKeywordAssistant.class), mock(ResumeQuestionAssistant.class));

		AiReply reply = service.generateInterviewReply(new InterviewReplyCommand(
				"请介绍一次线上问题复盘。",
				"我排查了日志。",
				"FOLLOW_UP",
				0,
				2,
				List.of("定位过程")
		));

		assertThat(reply.spokenText()).isEqualTo("好的，我们继续。");
		assertThat(reply.decisionSuggestion()).isEqualTo("FOLLOW_UP");
		assertThat(reply.scoreSuggestion()).isNull();
	}

	@Test
	void should_map_resume_keyword_json_with_defaults_and_record_metrics() {
		ResumeKeywordAssistant assistant = mock(ResumeKeywordAssistant.class);
		when(assistant.extract("简历正文")).thenReturn("""
				{
				  "summary": null,
				  "keywords": null,
				  "experienceHighlights": null
				}
				""");

		ProviderMetricsService metricsService = new ProviderMetricsService();
		LangChain4jAiService service = createService(metricsService, mock(InterviewReplyAssistant.class), assistant, mock(ResumeQuestionAssistant.class));

		ResumeKeywordExtractionResult result = service.extractResumeKeywords("简历正文");

		assertThat(result.summary()).isEqualTo("候选人具备技术开发经验");
		assertThat(result.keywords()).isEmpty();
		assertThat(result.experienceHighlights()).isEmpty();
		ProviderMetricView metric = findMetric(metricsService, "AI_RESUME_KEYWORDS");
		assertThat(metric.provider()).isEqualTo("langchain4j");
		assertThat(metric.totalCalls()).isEqualTo(1);
		assertThat(metric.successCalls()).isEqualTo(1);
		assertThat(metric.failureCalls()).isEqualTo(0);
		verify(assistant).extract("简历正文");
	}

	@Test
	void should_map_resume_questions_json_with_defaults_and_limit() {
		ResumeQuestionAssistant assistant = mock(ResumeQuestionAssistant.class);
		when(assistant.generate(
				"5 年 Java 后端",
				List.of("Java", "Spring Boot"),
				List.of("项目经验"),
				List.of("Redis", "Kafka"),
				2
		)).thenReturn("""
				{
				  "questions": [
				    {
				      "title": "缓存设计",
				      "prompt": "你如何设计热点缓存失效策略？",
				      "targetKeyword": "Redis",
				      "difficulty": 3
				    },
				    {
				      "title": null,
				      "prompt": null,
				      "targetKeyword": null,
				      "difficulty": null
				    },
				    {
				      "title": "消息可靠性",
				      "prompt": "如何保证消息最终一致？",
				      "targetKeyword": "Kafka",
				      "difficulty": 2
				    }
				  ]
				}
				""");

		ProviderMetricsService metricsService = new ProviderMetricsService();
		LangChain4jAiService service = createService(metricsService, mock(InterviewReplyAssistant.class), mock(ResumeKeywordAssistant.class), assistant);

		List<GeneratedResumeQuestion> questions = service.generateResumeQuestions(new ResumeQuestionGenerationCommand(
				"5 年 Java 后端",
				List.of("Java", "Spring Boot"),
				List.of("项目经验"),
				List.of("Redis", "Kafka"),
				2
		));

		assertThat(questions).hasSize(2);
		assertThat(questions.get(0).title()).isEqualTo("缓存设计");
		assertThat(questions.get(0).prompt()).isEqualTo("你如何设计热点缓存失效策略？");
		assertThat(questions.get(0).targetKeyword()).isEqualTo("Redis");
		assertThat(questions.get(0).difficulty()).isEqualTo(3);
		assertThat(questions.get(1).title()).isEqualTo("面试题");
		assertThat(questions.get(1).prompt()).isEqualTo("请介绍一下你的相关经验");
		assertThat(questions.get(1).targetKeyword()).isNull();
		assertThat(questions.get(1).difficulty()).isEqualTo(2);
		ProviderMetricView metric = findMetric(metricsService, "AI_RESUME_QUESTIONS");
		assertThat(metric.provider()).isEqualTo("langchain4j");
		assertThat(metric.totalCalls()).isEqualTo(1);
		assertThat(metric.successCalls()).isEqualTo(1);
		assertThat(metric.failureCalls()).isEqualTo(0);
		verify(assistant).generate(
				"5 年 Java 后端",
				List.of("Java", "Spring Boot"),
				List.of("项目经验"),
				List.of("Redis", "Kafka"),
				2
		);
	}

	private LangChain4jAiService createService(
			ProviderMetricsService metricsService,
			InterviewReplyAssistant interviewReplyAssistant,
			ResumeKeywordAssistant resumeKeywordAssistant,
			ResumeQuestionAssistant resumeQuestionAssistant
	) {
		LangChain4jAssistantFactory factory = mock(LangChain4jAssistantFactory.class);
		when(factory.interviewReplyAssistant()).thenReturn(interviewReplyAssistant);
		when(factory.resumeKeywordAssistant()).thenReturn(resumeKeywordAssistant);
		when(factory.resumeQuestionAssistant()).thenReturn(resumeQuestionAssistant);
		return new LangChain4jAiService(factory, metricsService, new ObjectMapper());
	}

	private ProviderMetricView findMetric(ProviderMetricsService metricsService, String capability) {
		return metricsService.snapshot().stream()
				.filter(metric -> capability.equals(metric.capability()))
				.findFirst()
				.orElseThrow();
	}
}
