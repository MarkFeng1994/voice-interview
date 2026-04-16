package com.interview.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.interview.module.interview.websocket.InterviewWebSocketHandler;
import com.interview.module.interview.websocket.InterviewWebSocketHandshakeInterceptor;
import com.interview.module.interview.websocket.RealtimeHandshakeInterceptor;
import com.interview.module.interview.websocket.RealtimeProxyWebSocketHandler;

@Configuration
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

	private final InterviewWebSocketHandler interviewWebSocketHandler;
	private final InterviewWebSocketHandshakeInterceptor handshakeInterceptor;
	private final RealtimeProxyWebSocketHandler realtimeProxyWebSocketHandler;
	private final RealtimeHandshakeInterceptor realtimeHandshakeInterceptor;

	public InterviewWebSocketConfig(
			InterviewWebSocketHandler interviewWebSocketHandler,
			InterviewWebSocketHandshakeInterceptor handshakeInterceptor,
			RealtimeProxyWebSocketHandler realtimeProxyWebSocketHandler,
			RealtimeHandshakeInterceptor realtimeHandshakeInterceptor
	) {
		this.interviewWebSocketHandler = interviewWebSocketHandler;
		this.handshakeInterceptor = handshakeInterceptor;
		this.realtimeProxyWebSocketHandler = realtimeProxyWebSocketHandler;
		this.realtimeHandshakeInterceptor = realtimeHandshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(interviewWebSocketHandler, "/ws/interview")
				.addInterceptors(handshakeInterceptor)
				.setAllowedOriginPatterns("*");

		registry.addHandler(realtimeProxyWebSocketHandler, "/ws/realtime")
				.addInterceptors(realtimeHandshakeInterceptor)
				.setAllowedOriginPatterns("*");
	}
}
