package com.interview.module.tts.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.tts", name = "provider", havingValue = "mock")
public class MockTtsService implements TtsService {

	@Override
	public TtsAudioResult synthesize(String text, TtsRenderOptions options) {
		String fileId = "mock-tts-" + UUID.randomUUID().toString().replace("-", "");
		return new TtsAudioResult(fileId, "/api/media/" + fileId, 1000L);
	}
}
