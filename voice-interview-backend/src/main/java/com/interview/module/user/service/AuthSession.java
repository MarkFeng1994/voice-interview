package com.interview.module.user.service;

public record AuthSession(
		String token,
		long expiresIn,
		UserProfile profile
) {
}
