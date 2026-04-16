package com.interview.module.interview.websocket;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

	private final InterviewWsTicketService interviewWsTicketService;

	public RealtimeHandshakeInterceptor(InterviewWsTicketService interviewWsTicketService) {
		this.interviewWsTicketService = interviewWsTicketService;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
	) {
		String ticket = UriComponentsBuilder.fromUri(request.getURI())
				.build()
				.getQueryParams()
				.getFirst("ticket");
		if (!StringUtils.hasText(ticket)) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		try {
			InterviewWsTicketPrincipal principal = interviewWsTicketService.parseRealtimeTicket(ticket.trim());
			attributes.put(InterviewWebSocketHandler.ATTR_USER_ID, principal.userId());
			attributes.put(InterviewWebSocketHandler.ATTR_SESSION_ID, principal.sessionId());
			return true;
		} catch (Exception ex) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception
	) {
		// no-op
	}
}
