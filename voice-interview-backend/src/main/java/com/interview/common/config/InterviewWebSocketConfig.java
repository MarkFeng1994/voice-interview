package com.interview.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.interview.module.interview.websocket.InterviewWebSocketHandler;
import com.interview.module.interview.websocket.InterviewWebSocketHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

	private final InterviewWebSocketHandler interviewWebSocketHandler;
	private final InterviewWebSocketHandshakeInterceptor handshakeInterceptor;

	public InterviewWebSocketConfig(
			InterviewWebSocketHandler interviewWebSocketHandler,
			InterviewWebSocketHandshakeInterceptor handshakeInterceptor
	) {
		this.interviewWebSocketHandler = interviewWebSocketHandler;
		this.handshakeInterceptor = handshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(interviewWebSocketHandler, "/ws/interview")
				.addInterceptors(handshakeInterceptor)
				.setAllowedOriginPatterns("*");
	}
}
