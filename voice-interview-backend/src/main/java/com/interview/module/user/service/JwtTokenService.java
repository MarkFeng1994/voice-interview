package com.interview.module.user.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.interview.common.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtTokenService {

	private final JwtProperties jwtProperties;
	private final Key signingKey;

	public JwtTokenService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String issueToken(UserProfile profile) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(jwtProperties.getExpireSeconds());
		return Jwts.builder()
				.subject(profile.id())
				.claim("username", profile.username())
				.claim("nickname", profile.nickname())
				.issuer(jwtProperties.getIssuer())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();
	}

	public JwtPrincipal parseToken(String token) {
		Claims claims = Jwts.parser()
				.verifyWith((javax.crypto.SecretKey) signingKey)
				.requireIssuer(jwtProperties.getIssuer())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		return new JwtPrincipal(
				claims.getSubject(),
				String.valueOf(claims.get("username"))
		);
	}

	public long getExpireSeconds() {
		return jwtProperties.getExpireSeconds();
	}
}
