package com.interview.module.interview.websocket;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.service.InterviewPracticeService;

@Component
public class InterviewWebSocketHandler extends TextWebSocketHandler {

	public static final String ATTR_USER_ID = "voiceInterview.ws.userId";
	public static final String ATTR_SESSION_ID = "voiceInterview.ws.sessionId";

	private final InterviewWebSocketSessionRegistry registry;
	private final InterviewPracticeService interviewPracticeService;
	private final ObjectMapper objectMapper;

	public InterviewWebSocketHandler(
			InterviewWebSocketSessionRegistry registry,
			InterviewPracticeService interviewPracticeService,
			ObjectMapper objectMapper
	) {
		this.registry = registry;
		this.interviewPracticeService = interviewPracticeService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		Map<String, Object> attributes = session.getAttributes();
		String userId = String.valueOf(attributes.get(ATTR_USER_ID));
		String sessionId = String.valueOf(attributes.get(ATTR_SESSION_ID));
		registry.register(session, userId, sessionId);
		InterviewSessionView view = interviewPracticeService.getState(sessionId, userId);
		registry.sendSessionState(session, view, "SESSION_SNAPSHOT");
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		JsonNode payload = objectMapper.readTree(message.getPayload());
		String type = payload.path("type").asText("");
		String userId = String.valueOf(session.getAttributes().get(ATTR_USER_ID));
		String sessionId = String.valueOf(session.getAttributes().get(ATTR_SESSION_ID));

		if ("PING".equalsIgnoreCase(type)) {
			session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
			return;
		}

		if ("SYNC_REQUEST".equalsIgnoreCase(type)) {
			InterviewSessionView view = interviewPracticeService.getState(sessionId, userId);
			registry.sendSessionState(session, view, "SESSION_SNAPSHOT");
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		registry.unregister(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		registry.unregister(session);
		if (session.isOpen()) {
			session.close(CloseStatus.SERVER_ERROR);
		}
	}

	@EventListener
	public void onSessionUpdated(InterviewSessionUpdatedEvent event) {
		registry.broadcast(event.userId(), event.session(), "SESSION_UPDATED");
	}
}
