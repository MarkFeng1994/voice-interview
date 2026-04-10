package com.interview.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.config.OpenAiProperties;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.system.service.ProviderMetricsService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleAiServiceTest {

	@Test
	void should_fallback_to_streaming_responses_for_interview_reply() throws Exception {
		String responseJson = """
				{"spokenText":"好的，我们继续。","decisionSuggestion":"NEXT_QUESTION","scoreSuggestion":88}
				""";
		try (StubAiGateway gateway = new StubAiGateway(responseJson)) {
			OpenAiCompatibleAiService service = createService(gateway.baseUrl());

			AiReply reply = service.generateInterviewReply(new InterviewReplyCommand(
					"问题",
					"候选人的回答",
					"OPENING",
					0,
					2,
					List.of()
			));

			assertThat(reply.spokenText()).isEqualTo("好的，我们继续。");
			assertThat(reply.decisionSuggestion()).isEqualTo("NEXT_QUESTION");
			assertThat(reply.scoreSuggestion()).isEqualTo(88);
			assertThat(gateway.chatCalls()).isEqualTo(1);
			assertThat(gateway.responsesCalls()).isEqualTo(1);
			assertThat(gateway.lastResponsesRequestBody()).contains("\"stream\":true");
			assertThat(gateway.lastResponsesRequestBody()).contains("\"instructions\"");
			assertThat(gateway.lastResponsesRequestBody()).doesNotContain("\"messages\"");
		}
	}

	@Test
	void should_fallback_to_streaming_responses_for_resume_keyword_extraction() throws Exception {
		String responseJson = """
				{"summary":"三年 Java 后端开发经验","keywords":["Java","Spring Boot"],"experienceHighlights":["负责高并发服务治理"]}
				""";
		try (StubAiGateway gateway = new StubAiGateway(responseJson)) {
			OpenAiCompatibleAiService service = createService(gateway.baseUrl());

			ResumeKeywordExtractionResult result = service.extractResumeKeywords("简历正文");

			assertThat(result.summary()).isEqualTo("三年 Java 后端开发经验");
			assertThat(result.keywords()).containsExactly("Java", "Spring Boot");
			assertThat(result.experienceHighlights()).containsExactly("负责高并发服务治理");
			assertThat(gateway.chatCalls()).isEqualTo(1);
			assertThat(gateway.responsesCalls()).isEqualTo(1);
		}
	}

	private OpenAiCompatibleAiService createService(String baseUrl) {
		OpenAiProperties properties = new OpenAiProperties();
		properties.getAi().setBaseUrl(baseUrl);
		properties.getAi().setApiKey("test-key");
		properties.getAi().setModel("gpt-5.4-xhigh-px");
		return new OpenAiCompatibleAiService(
				RestClient.builder(),
				new ObjectMapper(),
				properties,
				new ProviderMetricsService()
		);
	}

	private static final class StubAiGateway implements AutoCloseable {

		private static final String CHAT_ERROR = """
				{"error":{"message":"Unsupported parameter: system_prompt","type":"invalid_request_error"}}
				""";

		private final HttpServer server;
		private final String responseJson;
		private final AtomicInteger chatCalls = new AtomicInteger();
		private final AtomicInteger responsesCalls = new AtomicInteger();
		private volatile String lastResponsesRequestBody = "";

		private StubAiGateway(String responseJson) throws IOException {
			this.responseJson = responseJson;
			this.server = HttpServer.create(new InetSocketAddress(0), 0);
			this.server.createContext("/chat/completions", this::handleChatCompletions);
			this.server.createContext("/responses", this::handleResponses);
			this.server.start();
		}

		private String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		private int chatCalls() {
			return chatCalls.get();
		}

		private int responsesCalls() {
			return responsesCalls.get();
		}

		private String lastResponsesRequestBody() {
			return lastResponsesRequestBody;
		}

		private void handleChatCompletions(HttpExchange exchange) throws IOException {
			chatCalls.incrementAndGet();
			writeResponse(exchange, 400, "application/json", CHAT_ERROR);
		}

		private void handleResponses(HttpExchange exchange) throws IOException {
			responsesCalls.incrementAndGet();
			lastResponsesRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String body = """
					event: response.output_text.done
					data: {"type":"response.output_text.done","text":%s}
					
					event: response.completed
					data: {"type":"response.completed"}
					
					""".formatted(asJsonString(responseJson));
			writeResponse(exchange, 200, "text/event-stream", body);
		}

		private String asJsonString(String value) {
			return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n") + "\"";
		}

		private void writeResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", contentType);
			exchange.sendResponseHeaders(status, payload.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(payload);
			}
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
