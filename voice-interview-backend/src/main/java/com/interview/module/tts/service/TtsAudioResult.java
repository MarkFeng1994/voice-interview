package com.interview.module.tts.service;

public record TtsAudioResult(
		String fileId,
		String audioUrl,
		long durationMs
) {
}
