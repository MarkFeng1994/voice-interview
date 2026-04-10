package com.interview.module.user.service;

public record UserAccount(
		String id,
		String username,
		String password,
		String nickname
) {

	public UserProfile toProfile() {
		return new UserProfile(id, username, nickname);
	}
}
