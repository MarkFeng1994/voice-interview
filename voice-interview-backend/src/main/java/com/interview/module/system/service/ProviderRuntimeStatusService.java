package com.interview.module.system.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.interview.common.config.DashScopeProperties;
import com.interview.common.config.OpenAiProperties;

@Service
public class ProviderRuntimeStatusService {

	private final String aiProvider;
	private final String asrProvider;
	private final String ttsProvider;
	private final OpenAiProperties openAiProperties;
	private final DashScopeProperties dashScopeProperties;

	public ProviderRuntimeStatusService(
			@Value("${app.ai.provider:mock}") String aiProvider,
			@Value("${app.asr.provider:mock}") String asrProvider,
			@Value("${app.tts.provider:mock}") String ttsProvider,
			OpenAiProperties openAiProperties,
			DashScopeProperties dashScopeProperties
	) {
		this.aiProvider = aiProvider;
		this.asrProvider = asrProvider;
		this.ttsProvider = ttsProvider;
		this.openAiProperties = openAiProperties;
		this.dashScopeProperties = dashScopeProperties;
	}

	public ProviderRuntimePayload inspect() {
		return new ProviderRuntimePayload(inspectAi(), inspectAsr(), inspectTts());
	}

	private CapabilityStatus inspectAi() {
		if ("mock".equalsIgnoreCase(aiProvider)) {
			return new CapabilityStatus("mock", "UP", "mock LLM", Map.of());
		}
		if ("openai".equalsIgnoreCase(aiProvider)) {
			return inspectSpringAi("openai");
		}
		if ("springai".equalsIgnoreCase(aiProvider)) {
			return inspectSpringAi("springai");
		}
		return new CapabilityStatus(aiProvider, "UNKNOWN", "Unknown LLM provider", Map.of());
	}

	private CapabilityStatus inspectSpringAi(String providerName) {
		String baseUrl = openAiProperties.resolveAiBaseUrl();
		String apiKey = openAiProperties.resolveAiApiKey();
		String model = openAiProperties.resolveAiModel();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("baseUrl", baseUrl);
		details.put("model", model);
		details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
		if (apiKey == null || apiKey.isBlank()) {
			return new CapabilityStatus(providerName, "DOWN", "LLM missing API Key", details);
		}
		return new CapabilityStatus(providerName, "CONFIGURED", "LLM configured", details);
	}

	private CapabilityStatus inspectAsr() {
		if ("mock".equalsIgnoreCase(asrProvider)) {
			return new CapabilityStatus("mock", "UP", "mock ASR", Map.of());
		}
		if ("openai".equalsIgnoreCase(asrProvider)) {
			String baseUrl = openAiProperties.resolveAsrBaseUrl();
			String apiKey = openAiProperties.resolveAsrApiKey();
			String model = openAiProperties.resolveAsrModel();
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("baseUrl", baseUrl);
			details.put("model", model);
			details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
			if (apiKey == null || apiKey.isBlank()) {
				return new CapabilityStatus("openai", "DOWN", "ASR missing API Key", details);
			}
			return new CapabilityStatus("openai", "CONFIGURED", "ASR configured", details);
		}
		if ("dashscope".equalsIgnoreCase(asrProvider)) {
			String baseUrl = dashScopeProperties.resolveAsrBaseUrl();
			String apiKey = dashScopeProperties.resolveAsrApiKey();
			String model = dashScopeProperties.resolveAsrModel();
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("baseUrl", baseUrl);
			details.put("model", model);
			details.put("language", dashScopeProperties.resolveAsrLanguage());
			details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
			if (apiKey == null || apiKey.isBlank()) {
				return new CapabilityStatus("dashscope", "DOWN", "DashScope ASR missing API Key", details);
			}
			return new CapabilityStatus("dashscope", "CONFIGURED", "DashScope realtime ASR configured", details);
		}
		return new CapabilityStatus(asrProvider, "UNKNOWN", "Unknown ASR provider", Map.of());
	}

	private CapabilityStatus inspectTts() {
		if ("mock".equalsIgnoreCase(ttsProvider)) {
			return new CapabilityStatus("mock", "UP", "mock TTS", Map.of());
		}
		if ("openai".equalsIgnoreCase(ttsProvider) || "mimo".equalsIgnoreCase(ttsProvider)) {
			String baseUrl = openAiProperties.resolveTtsBaseUrl();
			String apiKey = openAiProperties.resolveTtsApiKey();
			String model = openAiProperties.resolveTtsModel();
			String providerName = "mimo".equalsIgnoreCase(ttsProvider) ? "mimo" : "openai";
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("baseUrl", baseUrl);
			details.put("model", model);
			details.put("voice", openAiProperties.resolveTtsVoice());
			details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
			if (apiKey == null || apiKey.isBlank()) {
				return new CapabilityStatus(providerName, "DOWN", "TTS missing API Key", details);
			}
			return new CapabilityStatus(providerName, "CONFIGURED", "TTS configured", details);
		}
		if ("dashscope".equalsIgnoreCase(ttsProvider)) {
			String baseUrl = dashScopeProperties.resolveTtsBaseUrl();
			String apiKey = dashScopeProperties.resolveTtsApiKey();
			String model = dashScopeProperties.resolveTtsModel();
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("baseUrl", baseUrl);
			details.put("model", model);
			details.put("voice", dashScopeProperties.resolveTtsVoice());
			details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
			if (apiKey == null || apiKey.isBlank()) {
				return new CapabilityStatus("dashscope", "DOWN", "DashScope TTS missing API Key", details);
			}
			return new CapabilityStatus("dashscope", "CONFIGURED", "DashScope realtime TTS configured", details);
		}
		return new CapabilityStatus(ttsProvider, "UNKNOWN", "Unknown TTS provider", Map.of());
	}

	public record ProviderRuntimePayload(CapabilityStatus ai, CapabilityStatus asr, CapabilityStatus tts) {}

	public record CapabilityStatus(String provider, String status, String message, Map<String, Object> details) {}
}
