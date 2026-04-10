package com.interview.module.user.service;

public interface AuthService {

	AuthSession register(String username, String password, String nickname);

	AuthSession login(String username, String password);

	UserProfile getProfileByToken(String token);

	UserProfile updateProfileByToken(String token, String nickname);
}
