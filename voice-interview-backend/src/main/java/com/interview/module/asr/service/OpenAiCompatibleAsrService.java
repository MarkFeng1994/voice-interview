package com.interview.module.asr.service;

import java.nio.file.Files;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.exception.AppException;
import com.interview.common.config.OpenAiProperties;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "openai")
public class OpenAiCompatibleAsrService implements AsrService {

	private final RestClient restClient;
	private final OpenAiProperties openAiProperties;
	private final ProviderMetricsService providerMetricsService;

	public OpenAiCompatibleAsrService(
			RestClient.Builder restClientBuilder,
			OpenAiProperties openAiProperties,
			ProviderMetricsService providerMetricsService
	) {
		this.openAiProperties = openAiProperties;
		this.providerMetricsService = providerMetricsService;
		this.restClient = restClientBuilder
				.baseUrl(trimTrailingSlash(openAiProperties.resolveAsrBaseUrl()))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.resolveAsrApiKey())
				.build();
	}

	@Override
	public AsrTranscription transcribe(StoredMediaFile mediaFile) {
		return providerMetricsService.record("ASR", "openai", () -> {
			requireApiKey();

			try {
				ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(mediaFile.path())) {
					@Override
					public String getFilename() {
						return mediaFile.originalFileName();
					}
				};

				HttpHeaders fileHeaders = new HttpHeaders();
				fileHeaders.setContentType(MediaType.parseMediaType(mediaFile.contentType()));
				HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(resource, fileHeaders);

				MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
				bodyBuilder.part("model", openAiProperties.resolveAsrModel());
				bodyBuilder.part("file", fileEntity);

				JsonNode root = restClient.post()
						.uri("/audio/transcriptions")
						.contentType(MediaType.MULTIPART_FORM_DATA)
						.body(bodyBuilder.build())
						.retrieve()
						.body(JsonNode.class);

				String transcript = root.path("text").asText();
				Double confidence = root.path("confidence").isNumber() ? root.path("confidence").asDouble() : null;
				return new AsrTranscription("openai", transcript, confidence);
			} catch (Exception ex) {
				throw AppException.serviceUnavailable("ASR_PROVIDER_UNAVAILABLE", "OpenAI ASR 调用失败", ex);
			}
		});
	}

	private void requireApiKey() {
		if (openAiProperties.resolveAsrApiKey() == null || openAiProperties.resolveAsrApiKey().isBlank()) {
			throw AppException.conflict("ASR_CONFIG_MISSING", "app.openai.asr.api-key 或 app.openai.api-key 未配置");
		}
	}

	private String trimTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
