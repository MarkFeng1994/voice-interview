package com.interview.module.tts.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.interview.common.exception.AppException;
import com.interview.common.config.OpenAiProperties;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.tts", name = "provider", havingValue = "openai")
public class OpenAiCompatibleTtsService implements TtsService {

	private final RestClient restClient;
	private final LocalMediaStorageService mediaStorageService;
	private final OpenAiProperties openAiProperties;
	private final ProviderMetricsService providerMetricsService;

	public OpenAiCompatibleTtsService(
			RestClient.Builder restClientBuilder,
			LocalMediaStorageService mediaStorageService,
			OpenAiProperties openAiProperties,
			ProviderMetricsService providerMetricsService
	) {
		this.mediaStorageService = mediaStorageService;
		this.openAiProperties = openAiProperties;
		this.providerMetricsService = providerMetricsService;
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(openAiProperties.resolveTtsBaseUrl()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolveTtsApiKey())
				.build();
	}

	@Override
	public TtsAudioResult synthesize(String text, TtsRenderOptions options) {
		return providerMetricsService.record("TTS", "openai", () -> {
			requireApiKey();

			byte[] audioBytes;
			try {
				audioBytes = restClient.post()
						.uri("/audio/speech")
						.contentType(MediaType.APPLICATION_JSON)
						.body(java.util.Map.of(
							"model", openAiProperties.resolveTtsModel(),
							"voice", openAiProperties.resolveTtsVoice(),
							"input", text == null ? "" : text,
							"instructions", openAiProperties.resolveTtsSpeechInstructions(),
								"response_format", "wav"
						))
						.retrieve()
						.body(byte[].class);
			} catch (Exception ex) {
				throw AppException.serviceUnavailable("TTS_PROVIDER_UNAVAILABLE", "OpenAI TTS 调用失败", ex);
			}

			StoredMediaFile storedFile = mediaStorageService.storeGenerated(
					audioBytes,
					"openai-tts.wav",
					"audio/wav",
					estimateDurationMs(text)
			);
			String audioUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
					.path("/api/media/")
					.path(storedFile.fileId())
					.toUriString();
			return new TtsAudioResult(storedFile.fileId(), audioUrl, estimateDurationMs(text));
		});
	}

	private long estimateDurationMs(String text) {
		String safeText = text == null ? "" : text;
		return Math.max(1200L, Math.min(12_000L, safeText.length() * 90L));
	}

	private void requireApiKey() {
		if (openAiProperties.resolveTtsApiKey() == null || openAiProperties.resolveTtsApiKey().isBlank()) {
			throw AppException.conflict("TTS_CONFIG_MISSING", "app.openai.tts.api-key 或 app.openai.api-key 未配置");
		}
	}

	private String trimTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
