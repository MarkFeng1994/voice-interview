package com.interview.module.asr.service;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.interview.common.audio.PcmAudioUtils;
import com.interview.common.config.DashScopeProperties;
import com.interview.common.exception.AppException;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "dashscope")
public class DashScopeRealtimeAsrService implements AsrService {

	private static final int TARGET_SAMPLE_RATE = 16_000;
	private static final int CHUNK_SIZE = 32 * 1024;
	private static final int TIMEOUT_SECONDS = 90;

	private final DashScopeProperties dashScopeProperties;
	private final ProviderMetricsService providerMetricsService;

	public DashScopeRealtimeAsrService(
			DashScopeProperties dashScopeProperties,
			ProviderMetricsService providerMetricsService
	) {
		this.dashScopeProperties = dashScopeProperties;
		this.providerMetricsService = providerMetricsService;
	}

	@Override
	public AsrTranscription transcribe(StoredMediaFile mediaFile) {
		return providerMetricsService.record("ASR", "dashscope", () -> {
			requireApiKey();
			byte[] pcmBytes = PcmAudioUtils.toPcm16Mono(
					mediaFile.path(),
					mediaFile.contentType(),
					mediaFile.originalFileName(),
					TARGET_SAMPLE_RATE
			);
			if (pcmBytes.length == 0) {
				throw AppException.badRequest("ASR_AUDIO_EMPTY", "上传的音频为空");
			}

			CountDownLatch completed = new CountDownLatch(1);
			AtomicReference<String> transcriptRef = new AtomicReference<>("");
			AtomicReference<String> errorRef = new AtomicReference<>();
			OmniRealtimeConversation conversation = null;

			try {
				conversation = new OmniRealtimeConversation(buildParam(), new OmniRealtimeCallback() {
					@Override
					public void onEvent(JsonObject message) {
						String type = readString(message, "type");
						if ("conversation.item.input_audio_transcription.completed".equals(type)) {
							transcriptRef.set(readString(message, "transcript"));
							completed.countDown();
							return;
						}
						if ("conversation.item.input_audio_transcription.failed".equals(type) || "error".equals(type)) {
							errorRef.set(resolveErrorMessage(message, "DashScope ASR 识别失败"));
							completed.countDown();
						}
					}

					@Override
					public void onClose(int code, String reason) {
						if (completed.getCount() > 0 && reason != null && !reason.isBlank()) {
							errorRef.compareAndSet(null, "DashScope ASR 连接关闭: " + reason);
							completed.countDown();
						}
					}
				});

				conversation.connect();
				conversation.updateSession(buildConfig());
				for (byte[] chunk : PcmAudioUtils.chunk(pcmBytes, CHUNK_SIZE)) {
					conversation.appendAudio(Base64.getEncoder().encodeToString(chunk));
				}
				conversation.commit();
				conversation.endSession();

				if (!completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					throw AppException.serviceUnavailable("ASR_PROVIDER_TIMEOUT", "DashScope ASR 超时未返回结果", null);
				}

				if (errorRef.get() != null) {
					throw AppException.serviceUnavailable("ASR_PROVIDER_UNAVAILABLE", errorRef.get(), null);
				}

				String transcript = transcriptRef.get();
				if (transcript == null || transcript.isBlank()) {
					throw AppException.serviceUnavailable("ASR_PROVIDER_UNAVAILABLE", "DashScope ASR 未返回有效转写结果", null);
				}
				return new AsrTranscription("dashscope", transcript.trim(), null);
			} catch (NoApiKeyException ex) {
				throw AppException.conflict("ASR_CONFIG_MISSING", "DashScope ASR key 未配置");
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw AppException.serviceUnavailable("ASR_PROVIDER_UNAVAILABLE", "DashScope ASR 调用被中断", ex);
			} catch (AppException ex) {
				throw ex;
			} catch (Exception ex) {
				throw AppException.serviceUnavailable("ASR_PROVIDER_UNAVAILABLE", "DashScope ASR 调用失败", ex);
			} finally {
				safeClose(conversation);
			}
		});
	}

	private OmniRealtimeParam buildParam() {
		return OmniRealtimeParam.builder()
				.model(dashScopeProperties.resolveAsrModel())
				.url(trimTrailingSlash(dashScopeProperties.resolveAsrBaseUrl()))
				.apikey(dashScopeProperties.resolveAsrApiKey())
				.build();
	}

	private OmniRealtimeConfig buildConfig() {
		OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
		transcriptionParam.setLanguage(dashScopeProperties.resolveAsrLanguage());
		transcriptionParam.setInputSampleRate(TARGET_SAMPLE_RATE);
		transcriptionParam.setInputAudioFormat("pcm");

		return OmniRealtimeConfig.builder()
				.modalities(List.of(OmniRealtimeModality.TEXT))
				.enableTurnDetection(false)
				.transcriptionConfig(transcriptionParam)
				.build();
	}

	private String resolveErrorMessage(JsonObject message, String fallback) {
		JsonElement errorElement = message.get("error");
		if (errorElement != null && errorElement.isJsonObject()) {
			JsonObject errorObject = errorElement.getAsJsonObject();
			String detail = readString(errorObject, "message");
			if (detail != null && !detail.isBlank()) {
				return detail;
			}
		}
		String directMessage = readString(message, "message");
		if (directMessage != null && !directMessage.isBlank()) {
			return directMessage;
		}
		return fallback;
	}

	private String readString(JsonObject message, String field) {
		JsonElement element = message.get(field);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		return element.getAsString();
	}

	private void requireApiKey() {
		if (dashScopeProperties.resolveAsrApiKey() == null || dashScopeProperties.resolveAsrApiKey().isBlank()) {
			throw AppException.conflict("ASR_CONFIG_MISSING", "DashScope ASR key 未配置");
		}
	}

	private void safeClose(OmniRealtimeConversation conversation) {
		if (conversation == null) {
			return;
		}
		try {
			conversation.close();
		} catch (Exception ignored) {
		}
	}

	private String trimTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
