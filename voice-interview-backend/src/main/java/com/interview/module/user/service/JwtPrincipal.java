package com.interview.module.user.service;

public record JwtPrincipal(
		String userId,
		String username
) {
}
