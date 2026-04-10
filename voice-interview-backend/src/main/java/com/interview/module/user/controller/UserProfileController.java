package com.interview.module.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.user.service.AuthService;
import com.interview.module.user.service.CurrentUserResolver;
import com.interview.module.user.service.UserProfile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

	private final AuthService authService;
	private final CurrentUserResolver currentUserResolver;

	public UserProfileController(AuthService authService, CurrentUserResolver currentUserResolver) {
		this.authService = authService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/profile")
	public ApiResponse<UserProfile> profile(HttpServletRequest request) {
		return ApiResponse.success(currentUserResolver.requireProfile(request));
	}

	@PutMapping("/profile")
	public ApiResponse<UserProfile> updateProfile(
			HttpServletRequest httpServletRequest,
			@RequestBody UpdateProfileRequest request
	) {
		String authorization = httpServletRequest.getHeader("Authorization");
		return ApiResponse.success(authService.updateProfileByToken(authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim(), request.nickname()));
	}

	public record UpdateProfileRequest(
			String nickname
	) {
	}
}
