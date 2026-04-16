package com.interview.module.interview.websocket;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.interview.common.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class InterviewWsTicketService {

	private final Key signingKey;
	private final JwtProperties jwtProperties;
	private final long expireSeconds;

	public InterviewWsTicketService(
			JwtProperties jwtProperties,
			@Value("${app.security.ws-ticket-expire-seconds:300}") long expireSeconds
	) {
		this.jwtProperties = jwtProperties;
		this.expireSeconds = expireSeconds;
		this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String issue(String userId, String sessionId) {
		return issueWithPurpose(userId, sessionId, "interview-ws");
	}

	public String issueRealtimeTicket(String userId, String sessionId) {
		return issueWithPurpose(userId, sessionId, "realtime-ws");
	}

	private String issueWithPurpose(String userId, String sessionId, String purpose) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId)
				.claim("sessionId", sessionId)
				.claim("purpose", purpose)
				.issuer(jwtProperties.getIssuer())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(expireSeconds)))
				.signWith(signingKey)
				.compact();
	}

	public InterviewWsTicketPrincipal parse(String ticket) {
		return parseWithPurpose(ticket, "interview-ws");
	}

	public InterviewWsTicketPrincipal parseRealtimeTicket(String ticket) {
		return parseWithPurpose(ticket, "realtime-ws");
	}

	private InterviewWsTicketPrincipal parseWithPurpose(String ticket, String purpose) {
		Claims claims = Jwts.parser()
				.verifyWith((javax.crypto.SecretKey) signingKey)
				.requireIssuer(jwtProperties.getIssuer())
				.require("purpose", purpose)
				.build()
				.parseSignedClaims(ticket)
				.getPayload();
		return new InterviewWsTicketPrincipal(
				claims.getSubject(),
				String.valueOf(claims.get("sessionId"))
		);
	}
}
