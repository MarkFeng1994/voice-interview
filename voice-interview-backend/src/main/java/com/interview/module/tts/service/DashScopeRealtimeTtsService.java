package com.interview.module.tts.service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeAudioFormat;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.interview.common.audio.PcmAudioUtils;
import com.interview.common.config.DashScopeProperties;
import com.interview.common.exception.AppException;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.tts", name = "provider", havingValue = "dashscope")
public class DashScopeRealtimeTtsService implements TtsService {

	private static final int OUTPUT_SAMPLE_RATE = 24_000;
	private static final int TIMEOUT_SECONDS = 90;

	private final DashScopeProperties dashScopeProperties;
	private final LocalMediaStorageService mediaStorageService;
	private final ProviderMetricsService providerMetricsService;

	public DashScopeRealtimeTtsService(
			DashScopeProperties dashScopeProperties,
			LocalMediaStorageService mediaStorageService,
			ProviderMetricsService providerMetricsService
	) {
		this.dashScopeProperties = dashScopeProperties;
		this.mediaStorageService = mediaStorageService;
		this.providerMetricsService = providerMetricsService;
	}

	@Override
	public TtsAudioResult synthesize(String text, TtsRenderOptions options) {
		return providerMetricsService.record("TTS", "dashscope", () -> {
			requireApiKey();

			String safeText = text == null ? "" : text.trim();
			if (safeText.isBlank()) {
				throw AppException.badRequest("TTS_TEXT_EMPTY", "待合成文本不能为空");
			}

			CountDownLatch completed = new CountDownLatch(1);
			ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();
			AtomicReference<String> errorRef = new AtomicReference<>();
			QwenTtsRealtime qwenTtsRealtime = null;

			try {
				qwenTtsRealtime = new QwenTtsRealtime(buildParam(), new QwenTtsRealtimeCallback() {
					@Override
					public void onEvent(JsonObject message) {
						String type = readString(message, "type");
						if ("response.audio.delta".equals(type)) {
							String delta = readString(message, "delta");
							if (delta != null && !delta.isBlank()) {
								byte[] pcmChunk = Base64.getDecoder().decode(delta);
								pcmStream.writeBytes(pcmChunk);
							}
							return;
						}
						if ("response.done".equals(type)) {
							completed.countDown();
							return;
						}
						if ("error".equals(type)) {
							errorRef.set(resolveErrorMessage(message, "DashScope TTS 合成失败"));
							completed.countDown();
						}
					}

					@Override
					public void onClose(int code, String reason) {
						if (completed.getCount() > 0 && reason != null && !reason.isBlank()) {
							errorRef.compareAndSet(null, "DashScope TTS 连接关闭: " + reason);
							completed.countDown();
						}
					}
				});

				qwenTtsRealtime.connect();
				qwenTtsRealtime.updateSession(buildConfig(options));
				qwenTtsRealtime.appendText(safeText);
				qwenTtsRealtime.commit();

				if (!completed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					throw AppException.serviceUnavailable("TTS_PROVIDER_TIMEOUT", "DashScope TTS 超时未返回结果", null);
				}

				if (errorRef.get() != null) {
					throw AppException.serviceUnavailable("TTS_PROVIDER_UNAVAILABLE", errorRef.get(), null);
				}

				byte[] pcmBytes = pcmStream.toByteArray();
				if (pcmBytes.length == 0) {
					throw AppException.serviceUnavailable("TTS_PROVIDER_UNAVAILABLE", "DashScope TTS 未返回音频数据", null);
				}

				byte[] wavBytes = PcmAudioUtils.wrapPcm16MonoAsWav(pcmBytes, OUTPUT_SAMPLE_RATE);
				long durationMs = PcmAudioUtils.estimateDurationMs(pcmBytes.length, OUTPUT_SAMPLE_RATE);
				StoredMediaFile storedFile = mediaStorageService.storeGenerated(
						wavBytes,
						"dashscope-tts.wav",
						"audio/wav",
						durationMs
				);
				String audioUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
						.path("/api/media/")
						.path(storedFile.fileId())
						.toUriString();
				return new TtsAudioResult(storedFile.fileId(), audioUrl, durationMs);
			} catch (NoApiKeyException ex) {
				throw AppException.conflict("TTS_CONFIG_MISSING", "DashScope TTS key 未配置");
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw AppException.serviceUnavailable("TTS_PROVIDER_UNAVAILABLE", "DashScope TTS 调用被中断", ex);
			} catch (AppException ex) {
				throw ex;
			} catch (Exception ex) {
				throw AppException.serviceUnavailable("TTS_PROVIDER_UNAVAILABLE", "DashScope TTS 调用失败", ex);
			} finally {
				safeClose(qwenTtsRealtime);
			}
		});
	}

	private QwenTtsRealtimeParam buildParam() {
		return QwenTtsRealtimeParam.builder()
				.model(dashScopeProperties.resolveTtsModel())
				.url(trimTrailingSlash(dashScopeProperties.resolveTtsBaseUrl()))
				.apikey(dashScopeProperties.resolveTtsApiKey())
				.build();
	}

	private QwenTtsRealtimeConfig buildConfig(TtsRenderOptions options) {
		QwenTtsRealtimeConfig.QwenTtsRealtimeConfigBuilder<?, ?> builder = QwenTtsRealtimeConfig.builder()
				.voice(resolveVoice(options))
				.responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
				.mode(dashScopeProperties.resolveTtsMode());

		String instructions = buildInstructions(options);
		if (instructions != null && !instructions.isBlank()) {
			builder.instructions(instructions);
		}
		return builder.build();
	}

	private String resolveVoice(TtsRenderOptions options) {
		Integer speakerId = options.speakerId();
		if (speakerId == null) {
			return dashScopeProperties.resolveTtsVoice();
		}
		return switch (speakerId) {
			case 34, 12, 22 -> "Serena";
			case 35, 13, 23 -> "Ethan";
			case 36, 14, 24 -> "Chelsie";
			default -> "Cherry";
		};
	}

	private String buildInstructions(TtsRenderOptions options) {
		String base = dashScopeProperties.resolveTtsInstructions();
		Double speechSpeed = options.speechSpeed();
		if (speechSpeed == null) {
			return base;
		}

		String pacingHint;
		if (speechSpeed < 0.9d) {
			pacingHint = "Keep a slightly slower pace with clear pauses between key points.";
		} else if (speechSpeed > 1.1d) {
			pacingHint = "Keep a slightly faster pace while staying clear and professional.";
		} else {
			pacingHint = "Keep a natural conversational pace.";
		}

		if (base == null || base.isBlank()) {
			return pacingHint;
		}
		return base + " " + pacingHint;
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
		if (dashScopeProperties.resolveTtsApiKey() == null || dashScopeProperties.resolveTtsApiKey().isBlank()) {
			throw AppException.conflict("TTS_CONFIG_MISSING", "DashScope TTS key 未配置");
		}
	}

	private void safeClose(QwenTtsRealtime qwenTtsRealtime) {
		if (qwenTtsRealtime == null) {
			return;
		}
		try {
			qwenTtsRealtime.close();
		} catch (Exception ignored) {
		}
	}

	private String trimTrailingSlash(String value) {
		return value == null ? "" : value.replaceAll("/+$", "");
	}
}
