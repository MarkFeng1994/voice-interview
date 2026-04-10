package com.interview.module.ai.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.config.OpenAiProperties;
import com.interview.module.interview.service.InterviewAnswerAnalyzer;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai")
public class OpenAiCompatibleAiService implements AiService {

	private static final String SYSTEM_PROMPT = """
			You are a professional technical interviewer.
			Return JSON only with these fields:
			{
			  "spokenText": "string",
			  "decisionSuggestion": "FOLLOW_UP|NEXT_QUESTION|END_INTERVIEW",
			  "scoreSuggestion": 0-100 or null
			}
			Keep spokenText concise and natural for voice playback.
			""";

	private final RestClient restClient;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final OpenAiProperties openAiProperties;
	private final ProviderMetricsService providerMetricsService;

	public OpenAiCompatibleAiService(
			RestClient.Builder restClientBuilder,
			ObjectMapper objectMapper,
			OpenAiProperties openAiProperties,
			ProviderMetricsService providerMetricsService
	) {
		this.objectMapper = objectMapper;
		this.openAiProperties = openAiProperties;
		this.providerMetricsService = providerMetricsService;
		this.httpClient = HttpClient.newHttpClient();
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(openAiProperties.resolveAiBaseUrl()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolveAiApiKey())
				.build();
	}

	@Override
	public AiReply generateInterviewReply(String inputText) {
		return providerMetricsService.record("AI", "openai", () -> {
			requireApiKey();
			String content = invokeTextCompletion(SYSTEM_PROMPT, inputText, true);
			try {
				JsonNode contentJson = objectMapper.readTree(content);
				String spokenText = contentJson.path("spokenText").asText(content);
				String decisionSuggestion = contentJson.path("decisionSuggestion").asText("FOLLOW_UP");
				Integer scoreSuggestion = contentJson.path("scoreSuggestion").isNumber()
						? contentJson.path("scoreSuggestion").asInt()
						: null;
				return new AiReply(spokenText, decisionSuggestion, scoreSuggestion);
			} catch (Exception ex) {
				return new AiReply(content, "FOLLOW_UP", null);
			}
		});
	}

	private void requireApiKey() {
		if (openAiProperties.resolveAiApiKey() == null || openAiProperties.resolveAiApiKey().isBlank()) {
			throw new IllegalStateException("app.openai.ai.api-key or app.openai.api-key is required when app.ai.provider=openai");
		}
	}

	@Override
	public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
		return providerMetricsService.record("AI_RESUME_KEYWORDS", "openai", () -> {
			requireApiKey();
			String systemPrompt = """
					你是一个技术简历分析专家。请从候选人简历文本中提取技术关键词。
					返回 JSON 格式：
					{
					  "summary": "候选人技术背景概述（1-2句话）",
					  "keywords": ["关键词1", "关键词2"],
					  "experienceHighlights": ["核心经验亮点1", "核心经验亮点2"]
					}
					关键词聚焦于：编程语言、框架、中间件、数据库、云服务、架构模式等技术栈。
					keywords 最多 15 个，按重要程度排序。
					experienceHighlights 最多 5 条。
					""";
			JsonNode result = invokeJsonChat(systemPrompt, resumeText);
			String summary = result.path("summary").asText("候选人具备技术开发经验");
			List<String> keywords = jsonArrayToList(result.path("keywords"));
			List<String> highlights = jsonArrayToList(result.path("experienceHighlights"));
			return new ResumeKeywordExtractionResult(summary, keywords, highlights);
		});
	}

	@Override
	public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
		return providerMetricsService.record("AI_RESUME_QUESTIONS", "openai", () -> {
			requireApiKey();
			String systemPrompt = """
					你是一个技术面试出题专家。根据候选人简历信息生成面试题。
					返回 JSON 格式：
					{
					  "questions": [
					    {
					      "title": "题目标题（简短）",
					      "prompt": "面试官提问的完整内容（口语化、自然）",
					      "targetKeyword": "针对的技术关键词",
					      "difficulty": 1-3
					    }
					  ]
					}
					difficulty: 1=基础, 2=中等, 3=深入。
					题目要结合候选人实际经验，口语化表达，适合语音播放。
					每题 prompt 控制在 80 字以内。
					""";
			String userContent = "候选人概况：" + command.resumeSummary()
					+ "\n需要覆盖的关键词：" + String.join("、", command.missingKeywords())
					+ "\n已有题目（避免重复）：" + String.join("、", command.existingQuestionTitles())
					+ "\n需要生成 " + command.questionCount() + " 道题";
			JsonNode result = invokeJsonChat(systemPrompt, userContent);
			List<GeneratedResumeQuestion> questions = new ArrayList<>();
			JsonNode arr = result.path("questions");
			if (arr.isArray()) {
				for (JsonNode item : arr) {
					questions.add(new GeneratedResumeQuestion(
							item.path("title").asText("面试题"),
							item.path("prompt").asText("请介绍一下你的相关经验"),
							item.path("targetKeyword").asText(null),
							item.path("difficulty").asInt(2)));
				}
			}
			return questions.stream().limit(command.questionCount()).toList();
		});
	}

	@Override
	public InterviewAnswerAnalyzer.Analysis analyzeInterviewAnswer(
			String question,
			String answer,
			List<String> expectedPoints
	) {
		return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
	}

	private JsonNode invokeJsonChat(String systemPrompt, String userContent) {
		String content = invokeTextCompletion(systemPrompt, userContent, true);
		try {
			return objectMapper.readTree(content);
		} catch (Exception ex) {
			return objectMapper.createObjectNode();
		}
	}

	private String invokeTextCompletion(String systemPrompt, String userContent, boolean jsonOnly) {
		try {
			return invokeChatCompletions(systemPrompt, userContent, jsonOnly);
		} catch (HttpClientErrorException.BadRequest ex) {
			if (shouldFallbackToResponses(ex)) {
				return invokeStreamingResponses(systemPrompt, userContent);
			}
			throw ex;
		}
	}

	private String invokeChatCompletions(String systemPrompt, String userContent, boolean jsonOnly) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", openAiProperties.resolveAiModel());
		payload.put("messages", List.of(
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userContent == null ? "" : userContent)
		));
		if (jsonOnly) {
			payload.put("response_format", Map.of("type", "json_object"));
		}

		JsonNode root = restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(JsonNode.class);

		return root.path("choices").path(0).path("message").path("content").asText();
	}

	private boolean shouldFallbackToResponses(HttpClientErrorException.BadRequest ex) {
		String body = ex.getResponseBodyAsString();
		return body != null && body.contains("system_prompt");
	}

	private String invokeStreamingResponses(String systemPrompt, String userContent) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", openAiProperties.resolveAiModel());
		payload.put("instructions", systemPrompt);
		payload.put("input", userContent == null ? "" : userContent);
		payload.put("stream", true);

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(trimTrailingSlash(openAiProperties.resolveAiBaseUrl()) + "/responses"))
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolveAiApiKey())
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 400) {
				throw new IllegalStateException("Responses API request failed: " + response.body());
			}
			return extractStreamingResponseText(response.body());
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to invoke responses API", ex);
		}
	}

	private String extractStreamingResponseText(String responseBody) {
		StringBuilder text = new StringBuilder();
		for (String line : responseBody.split("\\R")) {
			if (line == null || !line.startsWith("data:")) {
				continue;
			}
			String json = line.substring(5).trim();
			if (json.isBlank()) {
				continue;
			}
			try {
				JsonNode event = objectMapper.readTree(json);
				String type = event.path("type").asText();
				if ("response.output_text.done".equals(type)) {
					String doneText = event.path("text").asText("");
					if (!doneText.isBlank()) {
						return doneText;
					}
				}
				if ("response.output_text.delta".equals(type)) {
					text.append(event.path("delta").asText(""));
					continue;
				}
				if ("response.output_item.done".equals(type)) {
					String itemText = extractTextFromOutputItem(event.path("item"));
					if (!itemText.isBlank()) {
						return itemText;
					}
				}
			} catch (Exception ignored) {
				// Ignore malformed SSE events and keep parsing the stream body.
			}
		}
		if (!text.isEmpty()) {
			return text.toString();
		}
		throw new IllegalStateException("Responses API stream did not contain output text");
	}

	private String extractTextFromOutputItem(JsonNode item) {
		JsonNode content = item.path("content");
		if (!content.isArray()) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode part : content) {
			if ("output_text".equals(part.path("type").asText())) {
				text.append(part.path("text").asText(""));
			}
		}
		return text.toString();
	}

	private List<String> jsonArrayToList(JsonNode arrayNode) {
		List<String> result = new ArrayList<>();
		if (arrayNode != null && arrayNode.isArray()) {
			for (JsonNode item : arrayNode) {
				String text = item.asText();
				if (text != null && !text.isBlank()) {
					result.add(text.trim());
				}
			}
		}
		return result;
	}

	private String trimTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
