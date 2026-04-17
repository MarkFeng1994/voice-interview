package com.interview.module.system.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.interview.common.config.DashScopeProperties;
import com.interview.common.config.OpenAiProperties;

class ProviderRuntimeStatusServiceTest {

	@Test
	void should_report_springai_ai_as_configured_when_api_key_present() {
		ProviderRuntimeStatusService service = new ProviderRuntimeStatusService(
				"springai",
				"mock",
				"mock",
				openAiWithAiConfig("https://api.openai.com/v1", "sk-test", "gpt-4o-mini"),
				new DashScopeProperties()
		);

		ProviderRuntimeStatusService.ProviderRuntimePayload payload = service.inspect();

		assertThat(payload.ai().provider()).isEqualTo("springai");
		assertThat(payload.ai().status()).isEqualTo("CONFIGURED");
		assertThat(payload.ai().message()).isEqualTo("LLM configured");
		assertThat(payload.ai().details()).containsEntry("baseUrl", "https://api.openai.com/v1");
		assertThat(payload.ai().details()).containsEntry("model", "gpt-4o-mini");
		assertThat(payload.ai().details()).containsEntry("apiKeyConfigured", true);
	}

	@Test
	void should_report_springai_ai_as_down_when_api_key_missing() {
		ProviderRuntimeStatusService service = new ProviderRuntimeStatusService(
				"springai",
				"mock",
				"mock",
				openAiWithAiConfig("https://api.openai.com/v1", "", "gpt-4o-mini"),
				new DashScopeProperties()
		);

		ProviderRuntimeStatusService.ProviderRuntimePayload payload = service.inspect();

		assertThat(payload.ai().provider()).isEqualTo("springai");
		assertThat(payload.ai().status()).isEqualTo("DOWN");
		assertThat(payload.ai().message()).isEqualTo("LLM missing API Key");
		assertThat(payload.ai().details()).containsEntry("apiKeyConfigured", false);
	}

	private OpenAiProperties openAiWithAiConfig(String baseUrl, String apiKey, String model) {
		OpenAiProperties properties = new OpenAiProperties();
		properties.getAi().setBaseUrl(baseUrl);
		properties.getAi().setApiKey(apiKey);
		properties.getAi().setModel(model);
		return properties;
	}
}
