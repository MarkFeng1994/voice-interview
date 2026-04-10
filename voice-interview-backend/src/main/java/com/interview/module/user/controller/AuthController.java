package com.interview.module.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.user.service.AuthService;
import com.interview.module.user.service.AuthSession;
import com.interview.module.user.service.UserProfile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ApiResponse<AuthPayload> register(@RequestBody RegisterRequest request) {
		AuthSession result = authService.register(
				request.username(),
				request.password(),
				request.nickname()
		);
		return ApiResponse.success(new AuthPayload(result.token(), result.expiresIn(), result.profile()));
	}

	@PostMapping("/login")
	public ApiResponse<AuthPayload> login(@RequestBody LoginRequest request) {
		AuthSession result = authService.login(
				request.username(),
				request.password()
		);
		return ApiResponse.success(new AuthPayload(result.token(), result.expiresIn(), result.profile()));
	}

	public record RegisterRequest(
			String username,
			String password,
			String nickname
	) {
	}

	public record LoginRequest(
			String username,
			String password
	) {
	}

	public record AuthPayload(
			String token,
			long expiresIn,
			UserProfile profile
	) {
	}
}
