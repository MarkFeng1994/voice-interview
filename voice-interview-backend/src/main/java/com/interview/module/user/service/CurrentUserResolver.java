package com.interview.module.user.service;

import org.springframework.stereotype.Component;

import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.user.security.AuthContext;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentUserResolver {

	public UserProfile requireProfile(HttpServletRequest request) {
		Object currentUser = request.getAttribute(AuthContext.CURRENT_USER_ATTRIBUTE);
		if (currentUser instanceof UserProfile profile) {
			return profile;
		}
		throw new IllegalArgumentException("当前请求没有有效的登录用户");
	}

	public InterviewSessionOwner requireOwner(HttpServletRequest request) {
		UserProfile profile = requireProfile(request);
		return new InterviewSessionOwner(profile.id(), profile.nickname());
	}
}
