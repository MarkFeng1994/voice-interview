package com.interview.module.library.service;

public record LibraryQuestion(
		String id,
		String userId,
		String categoryId,
		String title,
		String content,
		String answer,
		Integer difficulty,
		String source,
		String sourceUrl
) {
}
