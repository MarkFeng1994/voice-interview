package com.interview.module.asr.service;

import com.interview.module.media.service.StoredMediaFile;

public interface AsrService {

	AsrTranscription transcribe(StoredMediaFile mediaFile);
}
