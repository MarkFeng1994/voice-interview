package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

	private String baseUrl = "https://api.openai.com/v1";
	private String apiKey = "";
	private String chatModel = "gpt-4o-mini";
	private String transcriptionModel = "gpt-4o-mini-transcribe";
	private String speechModel = "gpt-4o-mini-tts";
	private String voice = "alloy";
	private String speechInstructions = "Speak like a calm but professional technical interviewer.";
	private final EndpointProperties ai = new EndpointProperties();
	private final EndpointProperties asr = new EndpointProperties();
	private final SpeechEndpointProperties tts = new SpeechEndpointProperties();

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

	public String getChatModel() {
		return chatModel;
	}

	public void setChatModel(String chatModel) {
		this.chatModel = chatModel;
	}

	public String getTranscriptionModel() {
		return transcriptionModel;
	}

	public void setTranscriptionModel(String transcriptionModel) {
		this.transcriptionModel = transcriptionModel;
	}

	public String getSpeechModel() {
		return speechModel;
	}

	public void setSpeechModel(String speechModel) {
		this.speechModel = speechModel;
	}

	public String getVoice() {
		return voice;
	}

	public void setVoice(String voice) {
		this.voice = voice;
	}

	public String getSpeechInstructions() {
		return speechInstructions;
	}

	public void setSpeechInstructions(String speechInstructions) {
		this.speechInstructions = speechInstructions;
	}

	public EndpointProperties getAi() {
		return ai;
	}

	public EndpointProperties getAsr() {
		return asr;
	}

	public SpeechEndpointProperties getTts() {
		return tts;
	}

	public String resolveAiBaseUrl() {
		return firstNonBlank(ai.getBaseUrl(), baseUrl);
	}

	public String resolveAiApiKey() {
		return firstNonBlank(ai.getApiKey(), apiKey);
	}

	public String resolveAiModel() {
		return firstNonBlank(ai.getModel(), chatModel);
	}

	public String resolveAsrBaseUrl() {
		return firstNonBlank(asr.getBaseUrl(), baseUrl);
	}

	public String resolveAsrApiKey() {
		return firstNonBlank(asr.getApiKey(), apiKey);
	}

	public String resolveAsrModel() {
		return firstNonBlank(asr.getModel(), transcriptionModel);
	}

	public String resolveTtsBaseUrl() {
		return firstNonBlank(tts.getBaseUrl(), baseUrl);
	}

	public String resolveTtsApiKey() {
		return firstNonBlank(tts.getApiKey(), apiKey);
	}

	public String resolveTtsModel() {
		return firstNonBlank(tts.getModel(), speechModel);
	}

	public String resolveTtsVoice() {
		return firstNonBlank(tts.getVoice(), voice);
	}

	public String resolveTtsSpeechInstructions() {
		return firstNonBlank(tts.getSpeechInstructions(), speechInstructions);
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

	public static class SpeechEndpointProperties extends EndpointProperties {

		private String voice;
		private String speechInstructions;

		public String getVoice() {
			return voice;
		}

		public void setVoice(String voice) {
			this.voice = voice;
		}

		public String getSpeechInstructions() {
			return speechInstructions;
		}

		public void setSpeechInstructions(String speechInstructions) {
			this.speechInstructions = speechInstructions;
		}
	}
}
