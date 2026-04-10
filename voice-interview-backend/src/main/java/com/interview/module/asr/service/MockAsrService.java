package com.interview.module.asr.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.interview.module.media.service.StoredMediaFile;

@Service
@ConditionalOnProperty(prefix = "app.asr", name = "provider", havingValue = "mock")
public class MockAsrService implements AsrService {

	@Override
	public AsrTranscription transcribe(StoredMediaFile mediaFile) {
		return new AsrTranscription("mock", "这是 mock ASR 转写结果。", 1.0);
	}
}
