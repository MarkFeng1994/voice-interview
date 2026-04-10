import { buildAuthHeader } from '@/utils/auth'
import type { ProviderRuntimeResponse, TtsPreviewResponse } from '@/types/interview'

const request = async <T>(url: string, method: 'GET' | 'POST' = 'GET', data?: UniApp.RequestOptions['data']) => {
  const response = await uni.request({
    url,
    method,
    data,
    header: {
      ...buildAuthHeader(),
      ...(data ? { 'Content-Type': 'application/json' } : {}),
    },
  })
  return response.data as T
}

export const fetchProviderRuntime = (apiBaseUrl: string) =>
  request<ProviderRuntimeResponse>(`${apiBaseUrl}/api/system/providers`)

export const previewTtsVoice = (
  apiBaseUrl: string,
  payload: { text: string; interviewerSpeakerId: number; interviewerSpeechSpeed: number },
) =>
  request<TtsPreviewResponse>(`${apiBaseUrl}/api/system/tts-preview`, 'POST', payload)
