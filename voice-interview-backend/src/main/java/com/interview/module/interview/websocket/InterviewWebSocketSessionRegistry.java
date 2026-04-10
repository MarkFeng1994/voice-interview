package com.interview.module.interview.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.engine.model.InterviewSessionView;

@Component
public class InterviewWebSocketSessionRegistry {

	private static final Logger log = LoggerFactory.getLogger(InterviewWebSocketSessionRegistry.class);

	private final Map<String, SessionBinding> sessionsById = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;

	public InterviewWebSocketSessionRegistry(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void register(WebSocketSession session, String userId, String sessionId) {
		sessionsById.put(session.getId(), new SessionBinding(session, userId, sessionId));
	}

	public void unregister(WebSocketSession session) {
		if (session != null) {
			sessionsById.remove(session.getId());
		}
	}

	public void sendSessionState(WebSocketSession session, InterviewSessionView view, String eventType) {
		try {
			String payload = objectMapper.writeValueAsString(new SessionMessage(eventType, view));
			session.sendMessage(new TextMessage(payload));
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to send websocket session state", ex);
		}
	}

	public void broadcast(String userId, InterviewSessionView view, String eventType) {
		Set<String> sessionIds = sessionsById.keySet();
		for (String key : sessionIds) {
			SessionBinding binding = sessionsById.get(key);
			if (binding == null) {
				continue;
			}
			if (!Objects.equals(binding.userId(), userId) || !Objects.equals(binding.sessionId(), view.sessionId())) {
				continue;
			}
			try {
				sendSessionState(binding.session(), view, eventType);
			} catch (Exception ex) {
				log.warn("Failed to push websocket update, closing session {}", binding.session().getId(), ex);
				unregister(binding.session());
				try {
					binding.session().close();
				} catch (IOException closeEx) {
					log.debug("Failed to close broken websocket session", closeEx);
				}
			}
		}
	}

	public record SessionMessage(
			String type,
			InterviewSessionView session
	) {
	}

	private record SessionBinding(
			WebSocketSession session,
			String userId,
			String sessionId
	) {
	}
}
