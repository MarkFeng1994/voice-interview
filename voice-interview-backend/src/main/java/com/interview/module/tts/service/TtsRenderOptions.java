package com.interview.module.tts.service;

public record TtsRenderOptions(
		Integer speakerId,
		Double speechSpeed
) {

	public static TtsRenderOptions defaults() {
		return new TtsRenderOptions(null, null);
	}
}
