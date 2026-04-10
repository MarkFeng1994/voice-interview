package com.interview.module.asr.service;

public record AsrTranscription(
		String provider,
		String transcript,
		Double confidence
) {
}
