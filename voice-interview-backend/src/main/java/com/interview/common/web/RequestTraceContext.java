package com.interview.common.web;

public final class RequestTraceContext {

	private RequestTraceContext() {
	}

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String REQUEST_ID_ATTRIBUTE = "voiceInterview.requestId";
}
