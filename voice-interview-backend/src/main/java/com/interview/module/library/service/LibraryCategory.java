package com.interview.module.library.service;

public record LibraryCategory(
		String id,
		String userId,
		String name,
		String parentId,
		Integer sortOrder
) {
}
