package com.interview.module.tts.service;

public interface TtsService {

	default TtsAudioResult synthesize(String text) {
		return synthesize(text, TtsRenderOptions.defaults());
	}

	TtsAudioResult synthesize(String text, TtsRenderOptions options);
}
