package com.interview.module.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.google.gson.JsonObject;
import com.interview.common.config.DashScopeProperties;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;

@Service
public class RealtimeFallbackService {

	private static final Logger log = LoggerFactory.getLogger(RealtimeFallbackService.class);

	private final InterviewSessionStore sessionStore;
	private final DashScopeProperties dashScopeProperties;

	public RealtimeFallbackService(InterviewSessionStore sessionStore, DashScopeProperties dashScopeProperties) {
		this.sessionStore = sessionStore;
		this.dashScopeProperties = dashScopeProperties;
	}

	public FallbackResult fallbackToHalfDuplex(InterviewSessionState state, String reason) {
		state.setInterviewMode("standard");
		sessionStore.save(state);
		log.info("Fallback to half-duplex: session={}, reason={}", state.getSessionId(), reason);
		return new FallbackResult(state.getSessionId(), state.toView(), reason);
	}

	public RealtimeCapability checkCapability() {
		boolean available = isDashScopeRealtimeAvailable();
		return new RealtimeCapability(
				available,
				available ? "全双工模式可用" : "DashScope Realtime 服务不可用，将使用标准模式"
		);
	}

	private boolean isDashScopeRealtimeAvailable() {
		try {
			OmniRealtimeParam param = OmniRealtimeParam.builder()
					.model(dashScopeProperties.resolveRealtimeModel())
					.url(dashScopeProperties.resolveRealtimeBaseUrl())
					.apikey(dashScopeProperties.resolveRealtimeApiKey())
					.build();
			OmniRealtimeConversation probe = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
				@Override
				public void onEvent(JsonObject event) {
				}

				@Override
				public void onClose(int code, String reason) {
				}
			});
			probe.connect();
			probe.close();
			return true;
		} catch (Exception e) {
			log.warn("DashScope realtime probe failed: {}", e.getMessage());
			return false;
		}
	}

	public record FallbackResult(String sessionId, InterviewSessionView view, String reason) {
	}

	public record RealtimeCapability(boolean available, String message) {
	}
}
