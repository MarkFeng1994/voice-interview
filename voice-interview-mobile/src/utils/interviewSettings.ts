export interface InterviewVoiceSettings {
  interviewerSpeakerId: number
  interviewerSpeechSpeed: number
}

const STORAGE_KEY = 'voice-interview:voice-settings'

const DEFAULT_SETTINGS: InterviewVoiceSettings = {
  interviewerSpeakerId: 33,
  interviewerSpeechSpeed: 0.92,
}

export const readInterviewVoiceSettings = (): InterviewVoiceSettings => {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) {
      return { ...DEFAULT_SETTINGS }
    }
    return {
      interviewerSpeakerId: Number(raw.interviewerSpeakerId) || DEFAULT_SETTINGS.interviewerSpeakerId,
      interviewerSpeechSpeed: Number(raw.interviewerSpeechSpeed) || DEFAULT_SETTINGS.interviewerSpeechSpeed,
    }
  } catch {
    return { ...DEFAULT_SETTINGS }
  }
}

export const saveInterviewVoiceSettings = (settings: InterviewVoiceSettings) => {
  uni.setStorageSync(STORAGE_KEY, settings)
}
