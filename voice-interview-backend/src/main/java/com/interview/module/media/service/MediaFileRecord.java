package com.interview.module.media.service;

import java.time.LocalDateTime;

public record MediaFileRecord(
		String id,
		String userId,
		String bizType,
		String storageType,
		String fileKey,
		String mimeType,
		long durationMs,
		long sizeBytes,
		LocalDateTime expireAt
) {
}
