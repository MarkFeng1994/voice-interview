package com.interview.module.library.service;

import java.time.LocalDateTime;

public record ImportTaskRecord(
		String id,
		String userId,
		String type,
		String categoryId,
		String fileName,
		String sourceUrl,
		String status,
		int totalCount,
		int successCount,
		String errorMessage,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
