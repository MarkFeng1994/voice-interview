package com.interview.module.interview.websocket;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.interview.common.config.DashScopeProperties;
import com.interview.module.interview.engine.model.RealtimeMetrics;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;
import com.interview.module.interview.service.RealtimeFallbackService;
import com.interview.module.interview.service.RealtimeInterviewStateMachine;
import com.interview.module.interview.service.RealtimeInterviewStateMachine.TurnAction;
import com.interview.module.interview.service.RealtimeInterviewStateMachine.TurnResult;
import com.interview.module.interview.service.RealtimeSystemPromptBuilder;

@Component
public class RealtimeProxyWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(RealtimeProxyWebSocketHandler.class);

	private final ConcurrentHashMap<String, ProxySession> sessions = new ConcurrentHashMap<>();

	private final DashScopeProperties dashScopeProperties;
	private final RealtimeInterviewStateMachine stateMachine;
	private final InterviewSessionStore sessionStore;
	private final RealtimeFallbackService fallbackService;
	private final ObjectMapper objectMapper;

	public RealtimeProxyWebSocketHandler(
			DashScopeProperties dashScopeProperties,
			RealtimeInterviewStateMachine stateMachine,
			InterviewSessionStore sessionStore,
			RealtimeFallbackService fallbackService,
			ObjectMapper objectMapper
	) {
		this.dashScopeProperties = dashScopeProperties;
		this.stateMachine = stateMachine;
		this.sessionStore = sessionStore;
		this.fallbackService = fallbackService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
		String userId = (String) clientSession.getAttributes().get(InterviewWebSocketHandler.ATTR_USER_ID);
		String sessionId = (String) clientSession.getAttributes().get(InterviewWebSocketHandler.ATTR_SESSION_ID);

		InterviewSessionState state = sessionStore.findById(sessionId)
				.orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));

		state.setInterviewMode("realtime");

			RealtimeMetrics metrics = new RealtimeMetrics();
			metrics.setRealtimeStartedAt(System.currentTimeMillis());
			state.setRealtimeMetrics(metrics);

		OmniRealtimeConversation dashscopeWs = new OmniRealtimeConversation(
				buildDashScopeParam(),
				new ProxyCallback(clientSession, state)
		);
		dashscopeWs.connect();

		OmniRealtimeConfig config = buildSessionConfig(state);
		dashscopeWs.updateSession(config);

		ProxySession proxySession = new ProxySession(clientSession, dashscopeWs, state);
		sessions.put(clientSession.getId(), proxySession);

		sessionStore.save(state);
		sendToClient(clientSession, Map.of("type", "session.ready", "sessionId", sessionId));
		log.info("Realtime proxy established: userId={}, sessionId={}", userId, sessionId);
	}

	@Override
	protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) throws Exception {
		ProxySession proxy = sessions.get(clientSession.getId());
		if (proxy == null) return;

		JsonNode msg = objectMapper.readTree(message.getPayload());
		String type = msg.path("type").asText();

		switch (type) {
			case "audio.append" -> {
				String base64Audio = msg.path("audio").asText();
				proxy.dashscopeWs().appendAudio(base64Audio);
			}
			case "conversation.interrupt" -> {
				proxy.dashscopeWs().cancelResponse();
				proxy.state().setLastInterruptedAt(System.currentTimeMillis());
				RealtimeMetrics m = proxy.state().getRealtimeMetrics();
				if (m != null) m.incrementInterrupts();
				sendToClient(clientSession, Map.of("type", "interrupt.ack"));
			}
			case "conversation.commit" -> {
				proxy.dashscopeWs().commit();
			}
			case "PING" -> {
				sendToClient(clientSession, Map.of("type", "PONG"));
			}
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) {
		ProxySession proxy = sessions.remove(clientSession.getId());
		if (proxy != null) {
			try {
				proxy.dashscopeWs().close();
			} catch (Exception ignored) {
			}
			log.info("Realtime proxy closed: clientSessionId={}, status={}", clientSession.getId(), status);
		}
	}

	private OmniRealtimeParam buildDashScopeParam() {
		return OmniRealtimeParam.builder()
				.model(dashScopeProperties.resolveRealtimeModel())
				.url(dashScopeProperties.resolveRealtimeBaseUrl())
				.apikey(dashScopeProperties.resolveRealtimeApiKey())
				.build();
	}

	private OmniRealtimeConfig buildSessionConfig(InterviewSessionState state) {
		return OmniRealtimeConfig.builder()
				.modalities(List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO))
				.enableTurnDetection(dashScopeProperties.resolveRealtimeEnableTurnDetection())
				.voice(dashScopeProperties.resolveRealtimeVoice())
				.parameters(Map.of("instructions", RealtimeSystemPromptBuilder.build(state)))
				.build();
	}

	private void sendToClient(WebSocketSession session, Map<String, Object> payload) {
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
		} catch (IOException e) {
			log.warn("Failed to send to client: {}", e.getMessage());
		}
	}

	private record ProxySession(
			WebSocketSession clientSession,
			OmniRealtimeConversation dashscopeWs,
			InterviewSessionState state
	) {
	}

	private class ProxyCallback extends OmniRealtimeCallback {

		private final WebSocketSession clientSession;
		private final InterviewSessionState state;
		private final StringBuilder currentAiText = new StringBuilder();
		private final StringBuilder currentUserText = new StringBuilder();

		ProxyCallback(WebSocketSession clientSession, InterviewSessionState state) {
			this.clientSession = clientSession;
			this.state = state;
		}

		@Override
		public void onOpen() {
			log.debug("DashScope WS connected for client={}", clientSession.getId());
		}

		@Override
		public void onEvent(JsonObject event) {
			String type = event.has("type") ? event.get("type").getAsString() : "";

			switch (type) {
				case "conversation.item.input_audio_transcription.delta" -> {
					String delta = event.has("delta") ? event.get("delta").getAsString() : "";
					currentUserText.append(delta);
					forwardToClient("transcript.user.delta", Map.of("delta", delta));
				}
				case "conversation.item.input_audio_transcription.completed" -> {
					String fullText = event.has("transcript") ? event.get("transcript").getAsString() : "";
					currentUserText.setLength(0);
					currentUserText.append(fullText);
					forwardToClient("transcript.user.completed", Map.of("text", fullText));
				}
				case "response.audio.delta" -> {
					String audioDelta = event.has("delta") ? event.get("delta").getAsString() : "";
					forwardToClient("audio.delta", Map.of("audio", audioDelta));
				}
				case "response.text.delta" -> {
					String textDelta = event.has("delta") ? event.get("delta").getAsString() : "";
					currentAiText.append(textDelta);
					forwardToClient("transcript.assistant.delta", Map.of("delta", textDelta));
				}
				case "response.done" -> {
					forwardToClient("audio.done", Map.of());
					handleAiResponseComplete();
				}
				case "error" -> {
					JsonObject error = event.has("error") ? event.getAsJsonObject("error") : new JsonObject();
					String code = error.has("code") ? error.get("code").getAsString() : "UNKNOWN";
					String message = error.has("message") ? error.get("message").getAsString() : "";
					log.error("DashScope error: code={}, message={}", code, message);
					forwardToClient("error", Map.of("code", code, "message", message));
				}
			}
		}

		@Override
		public void onClose(int code, String reason) {
			log.warn("DashScope WS closed: code={}, reason={}", code, reason);
			if (clientSession.isOpen()) {
				forwardToClient("error", Map.of(
						"code", "DASHSCOPE_DISCONNECTED",
						"message", "实时对话连接已断开"
				));
			}
		}

		private void handleAiResponseComplete() {
			String userAnswer = currentUserText.toString().trim();
			String aiReply = currentAiText.toString().trim();

			currentUserText.setLength(0);
			currentAiText.setLength(0);

			if (userAnswer.isEmpty()) return;

			CompletableFuture.runAsync(() -> {
				try {
					TurnResult result = stateMachine.processTurnComplete(state, userAnswer, aiReply);
					forwardToClient("session.updated", Map.of("session", result.view()));

					ProxySession proxy = sessions.get(clientSession.getId());
					if (proxy == null) return;

					switch (result.action()) {
						case CONTINUE -> { /* DashScope 继续监听 */ }
						case UPDATE_PROMPT -> {
							OmniRealtimeConfig newConfig = OmniRealtimeConfig.builder()
									.parameters(Map.of("instructions", result.newSystemPrompt()))
									.build();
							proxy.dashscopeWs().updateSession(newConfig);
						}
						case DISCONNECT -> {
							forwardToClient("session.completed", Map.of("session", result.view()));
							proxy.dashscopeWs().close();
						}
					}
				} catch (Exception e) {
					log.error("Turn processing failed: session={}", state.getSessionId(), e);
					forwardToClient("error", Map.of(
							"code", "TURN_PROCESS_ERROR",
							"message", "面试轮次处理失败"
					));
				}
			});
		}

		private void forwardToClient(String type, Map<String, Object> data) {
			try {
				Map<String, Object> payload = new java.util.HashMap<>(data);
				payload.put("type", type);
				clientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
			} catch (IOException e) {
				log.warn("Failed to forward to client: type={}, error={}", type, e.getMessage());
			}
		}
	}
}
