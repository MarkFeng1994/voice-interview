package com.interview.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = request.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
		if (!StringUtils.hasText(requestId)) {
			requestId = UUID.randomUUID().toString().replace("-", "");
		}

		request.setAttribute(RequestTraceContext.REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(RequestTraceContext.REQUEST_ID_HEADER, requestId);

		long startedAt = System.currentTimeMillis();
		String method = request.getMethod();
		String path = request.getRequestURI();
		log.info("request-start requestId={} method={} path={}", requestId, method, path);
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = System.currentTimeMillis() - startedAt;
			log.info(
					"request-end requestId={} method={} path={} status={} durationMs={}",
					requestId,
					method,
					path,
					response.getStatus(),
					durationMs
			);
		}
	}
}
