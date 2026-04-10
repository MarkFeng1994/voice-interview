package com.interview.module.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.config.OpenAiProperties;
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
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(openAiProperties.resolveAiBaseUrl()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolveAiApiKey())
				.build();
	}

	@Override
	public AiReply generateInterviewReply(String inputText) {
		return providerMetricsService.record("AI", "openai", () -> {
			requireApiKey();

			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("model", openAiProperties.resolveAiModel());
			payload.put("messages", List.of(
					Map.of("role", "system", "content", SYSTEM_PROMPT),
					Map.of("role", "user", "content", inputText == null ? "" : inputText)
			));
			payload.put("response_format", Map.of("type", "json_object"));

			JsonNode root = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(JsonNode.class);

			String content = root.path("choices").path(0).path("message").path("content").asText();
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

	private JsonNode invokeJsonChat(String systemPrompt, String userContent) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", openAiProperties.resolveAiModel());
		payload.put("messages", List.of(
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userContent == null ? "" : userContent)
		));
		payload.put("response_format", Map.of("type", "json_object"));

		JsonNode root = restClient.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(JsonNode.class);

		String content = root.path("choices").path(0).path("message").path("content").asText();
		try {
			return objectMapper.readTree(content);
		} catch (Exception ex) {
			return objectMapper.createObjectNode();
		}
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
