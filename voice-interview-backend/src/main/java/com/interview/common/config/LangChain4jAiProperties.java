package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.langchain4j.ai")
public class LangChain4jAiProperties {

	private double temperature = 0.2;
	private int timeoutSeconds = 60;
	private boolean strictJsonSchema = true;
	private int maxRetries = 1;

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public boolean isStrictJsonSchema() {
		return strictJsonSchema;
	}

	public void setStrictJsonSchema(boolean strictJsonSchema) {
		this.strictJsonSchema = strictJsonSchema;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}
}
