package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dashscope")
public class DashScopeProperties {

	private String baseUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
	private String apiKey = "";
	private final AsrEndpointProperties asr = new AsrEndpointProperties();
	private final TtsEndpointProperties tts = new TtsEndpointProperties();
	private final RealtimeEndpointProperties realtime = new RealtimeEndpointProperties();

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public AsrEndpointProperties getAsr() {
		return asr;
	}

	public TtsEndpointProperties getTts() {
		return tts;
	}

	public RealtimeEndpointProperties getRealtime() {
		return realtime;
	}

	public String resolveAsrBaseUrl() {
		return firstNonBlank(asr.getBaseUrl(), baseUrl);
	}

	public String resolveAsrApiKey() {
		return firstNonBlank(asr.getApiKey(), apiKey);
	}

	public String resolveAsrModel() {
		return firstNonBlank(asr.getModel(), "qwen3-asr-flash-realtime");
	}

	public String resolveAsrLanguage() {
		return firstNonBlank(asr.getLanguage(), "zh");
	}

	public String resolveTtsBaseUrl() {
		return firstNonBlank(tts.getBaseUrl(), baseUrl);
	}

	public String resolveTtsApiKey() {
		return firstNonBlank(tts.getApiKey(), apiKey);
	}

	public String resolveTtsModel() {
		return firstNonBlank(tts.getModel(), "qwen3-tts-flash-realtime");
	}

	public String resolveTtsVoice() {
		return firstNonBlank(tts.getVoice(), "Cherry");
	}

	public String resolveTtsMode() {
		return firstNonBlank(tts.getMode(), "commit");
	}

	public String resolveTtsInstructions() {
		return firstNonBlank(tts.getInstructions(), "Speak like a calm but professional technical interviewer.");
	}

	public String resolveRealtimeBaseUrl() {
		return firstNonBlank(realtime.getBaseUrl(), baseUrl);
	}

	public String resolveRealtimeApiKey() {
		return firstNonBlank(realtime.getApiKey(), apiKey);
	}

	public String resolveRealtimeModel() {
		return firstNonBlank(realtime.getModel(), "qwen-omni-turbo-realtime");
	}

	public String resolveRealtimeVoice() {
		return firstNonBlank(realtime.getVoice(), "Cherry");
	}

	public boolean resolveRealtimeEnableTurnDetection() {
		return realtime.isEnableTurnDetection();
	}

	private String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred;
		}
		return fallback;
	}

	public static class EndpointProperties {

		private String baseUrl;
		private String apiKey;
		private String model;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}
	}

	public static class AsrEndpointProperties extends EndpointProperties {

		private String language = "zh";

		public String getLanguage() {
			return language;
		}

		public void setLanguage(String language) {
			this.language = language;
		}
	}

	public static class TtsEndpointProperties extends EndpointProperties {

		private String voice = "Cherry";
		private String mode = "commit";
		private String instructions = "Speak like a calm but professional technical interviewer.";

		public String getVoice() {
			return voice;
		}

		public void setVoice(String voice) {
			this.voice = voice;
		}

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public String getInstructions() {
			return instructions;
		}

		public void setInstructions(String instructions) {
			this.instructions = instructions;
		}
	}

	public static class RealtimeEndpointProperties extends EndpointProperties {

		private String voice = "Cherry";
		private boolean enableTurnDetection = true;

		public String getVoice() {
			return voice;
		}

		public void setVoice(String voice) {
			this.voice = voice;
		}

		public boolean isEnableTurnDetection() {
			return enableTurnDetection;
		}

		public void setEnableTurnDetection(boolean enableTurnDetection) {
			this.enableTurnDetection = enableTurnDetection;
		}
	}
}
