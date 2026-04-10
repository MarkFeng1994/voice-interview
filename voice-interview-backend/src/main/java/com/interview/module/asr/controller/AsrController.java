package com.interview.module.asr.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.asr.service.AsrService;
import com.interview.module.asr.service.AsrTranscription;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;

@RestController
@RequestMapping("/api/asr")
public class AsrController {

	private final LocalMediaStorageService mediaStorageService;
	private final AsrService asrService;

	public AsrController(LocalMediaStorageService mediaStorageService, AsrService asrService) {
		this.mediaStorageService = mediaStorageService;
		this.asrService = asrService;
	}

	@PostMapping("/transcriptions/{fileId}")
	public ApiResponse<AsrTranscriptionPayload> transcribe(@PathVariable String fileId) {
		StoredMediaFile mediaFile = mediaStorageService.load(fileId);
		AsrTranscription transcription = asrService.transcribe(mediaFile);
		return ApiResponse.success(new AsrTranscriptionPayload(
				fileId,
				transcription.provider(),
				transcription.transcript(),
				transcription.confidence()
		));
	}

	public record AsrTranscriptionPayload(
			String fileId,
			String provider,
			String transcript,
			Double confidence
	) {
	}
}
