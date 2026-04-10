package com.interview.module.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.media.service.MediaFileLifecycleService;
import com.interview.module.tts.service.TtsAudioResult;
import com.interview.module.tts.service.TtsRenderOptions;
import com.interview.module.tts.service.TtsService;
import com.interview.module.system.service.ProviderMetricsService;
import com.interview.module.system.service.ProviderMetricsService.ProviderMetricView;
import com.interview.module.system.service.ProviderRuntimeStatusService;
import com.interview.module.system.service.ProviderRuntimeStatusService.ProviderRuntimePayload;

@RestController
@RequestMapping("/api/system")
public class SystemController {

	private final ProviderRuntimeStatusService providerRuntimeStatusService;
	private final MediaFileLifecycleService mediaFileLifecycleService;
	private final ProviderMetricsService providerMetricsService;
	private final TtsService ttsService;

	public SystemController(
			ProviderRuntimeStatusService providerRuntimeStatusService,
			org.springframework.beans.factory.ObjectProvider<MediaFileLifecycleService> mediaFileLifecycleService,
			ProviderMetricsService providerMetricsService,
			TtsService ttsService
	) {
		this.providerRuntimeStatusService = providerRuntimeStatusService;
		this.mediaFileLifecycleService = mediaFileLifecycleService.getIfAvailable();
		this.providerMetricsService = providerMetricsService;
		this.ttsService = ttsService;
	}

	@GetMapping("/providers")
	public ApiResponse<ProviderRuntimePayload> providers() {
		return ApiResponse.success(providerRuntimeStatusService.inspect());
	}

	@GetMapping("/metrics")
	public ApiResponse<java.util.List<ProviderMetricView>> metrics() {
		return ApiResponse.success(providerMetricsService.snapshot());
	}

	@PostMapping("/tts-preview")
	public ApiResponse<TtsPreviewPayload> ttsPreview(@RequestBody TtsPreviewRequest request) {
		TtsAudioResult result = ttsService.synthesize(
				request.text(),
				new TtsRenderOptions(request.interviewerSpeakerId(), request.interviewerSpeechSpeed())
		);
		return ApiResponse.success(new TtsPreviewPayload(
				request.text(),
				result.fileId(),
				result.audioUrl(),
				result.durationMs()
		));
	}

	@PostMapping("/media/cleanup")
	public ApiResponse<MediaCleanupPayload> cleanupMedia() {
		if (mediaFileLifecycleService == null) {
			return ApiResponse.success(new MediaCleanupPayload(0, "当前环境未启用媒体清理"));
		}
		int deleted = mediaFileLifecycleService.cleanupExpiredFiles(500);
		return ApiResponse.success(new MediaCleanupPayload(deleted, "媒体清理已执行"));
	}

	public record MediaCleanupPayload(
			int deletedCount,
			String message
	) {
	}

	public record TtsPreviewRequest(
			String text,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
	}

	public record TtsPreviewPayload(
			String text,
			String fileId,
			String audioUrl,
			long audioDurationMs
	) {
	}
}
