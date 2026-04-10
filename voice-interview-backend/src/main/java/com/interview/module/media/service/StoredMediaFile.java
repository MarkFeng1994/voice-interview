package com.interview.module.media.service;

import java.nio.file.Path;

public record StoredMediaFile(
		String fileId,
		String originalFileName,
		String storedFileName,
		String contentType,
		long size,
		Path path
) {
}
