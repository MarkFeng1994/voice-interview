package com.interview.module.user.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.result.ApiResponse;
import com.interview.module.user.service.AuthService;
import com.interview.module.user.service.UserProfile;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
			"/health",
			"/actuator/health",
			"/api/auth/login",
			"/api/auth/register"
	);

	private final AuthService authService;
	private final ObjectMapper objectMapper;

	public JwtAuthenticationFilter(AuthService authService, ObjectMapper objectMapper) {
		this.authService = authService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return CorsUtils.isPreFlightRequest(request)
				|| PUBLIC_ENDPOINTS.contains(path)
				|| isPublicMediaReadRequest(request, path);
	}

	private boolean isPublicMediaReadRequest(HttpServletRequest request, String path) {
		return "GET".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/media/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (!StringUtils.hasText(authorization)) {
			writeUnauthorized(response, "UNAUTHORIZED", "缺少 Authorization 头");
			return;
		}

		try {
			String token = authorization.startsWith("Bearer ")
					? authorization.substring(7).trim()
					: authorization.trim();
			UserProfile profile = authService.getProfileByToken(token);
			request.setAttribute(AuthContext.CURRENT_USER_ATTRIBUTE, profile);
			filterChain.doFilter(request, response);
		} catch (JwtException | IllegalArgumentException ex) {
			writeUnauthorized(response, "UNAUTHORIZED", ex.getMessage());
		}
	}

	private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
	}
}
