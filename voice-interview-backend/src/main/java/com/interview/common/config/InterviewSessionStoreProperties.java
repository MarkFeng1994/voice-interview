package com.interview.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.interview")
public class InterviewSessionStoreProperties {

	private String sessionStore = "memory";
	private String redisKeyPrefix = "voice-interview:session:";
	private Duration sessionTtl = Duration.ofHours(2);

	public String getSessionStore() {
		return sessionStore;
	}

	public void setSessionStore(String sessionStore) {
		this.sessionStore = sessionStore;
	}

	public String getRedisKeyPrefix() {
		return redisKeyPrefix;
	}

	public void setRedisKeyPrefix(String redisKeyPrefix) {
		this.redisKeyPrefix = redisKeyPrefix;
	}

	public Duration getSessionTtl() {
		return sessionTtl;
	}

	public void setSessionTtl(Duration sessionTtl) {
		this.sessionTtl = sessionTtl;
	}
}
