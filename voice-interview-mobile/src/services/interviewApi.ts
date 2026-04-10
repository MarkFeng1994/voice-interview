import { buildAuthHeader } from '@/utils/auth'
import type {
  InterviewPresetListResponse,
  InterviewReportResponse,
  InterviewReplyResponse,
  StartInterviewRequest,
  InterviewSessionSummaryListResponse,
  InterviewSessionStateResponse,
  InterviewWsTicketResponse,
  ResumePreviewRequest,
  ResumePreviewResponse,
} from '@/types/interview'

const request = async <T>(
  url: string,
  method: 'GET' | 'POST',
  data?: UniApp.RequestOptions['data'],
  header?: Record<string, string>,
) => {
  const response = await uni.request({
    url,
    method,
    data,
    header: {
      ...buildAuthHeader(),
      ...(header || {}),
    },
  })
  return response.data as T
}

export const startInterviewSession = (apiBaseUrl: string, payload?: StartInterviewRequest) =>
  request<InterviewSessionStateResponse>(
    `${apiBaseUrl}/api/interviews`,
    'POST',
    payload,
    payload ? { 'Content-Type': 'application/json' } : undefined,
  )

export const listInterviewPresets = (apiBaseUrl: string) =>
  request<InterviewPresetListResponse>(`${apiBaseUrl}/api/interviews/presets`, 'GET')

export const getInterviewSessionState = (apiBaseUrl: string, sessionId: string) =>
  request<InterviewSessionStateResponse>(`${apiBaseUrl}/api/interviews/${sessionId}/state`, 'GET')

export const issueInterviewWsTicket = (apiBaseUrl: string, sessionId: string) =>
  request<InterviewWsTicketResponse>(`${apiBaseUrl}/api/interviews/${sessionId}/ws-ticket`, 'POST')

export const submitInterviewAnswer = (
  apiBaseUrl: string,
  payload: { sessionId: string; fileId: string | null; textAnswer: string | null },
) =>
  request<InterviewSessionStateResponse>(
    `${apiBaseUrl}/api/interviews/${payload.sessionId}/answer`,
    'POST',
    payload,
    { 'Content-Type': 'application/json' },
  )

export const skipInterviewQuestion = (apiBaseUrl: string, sessionId: string) =>
  request<InterviewSessionStateResponse>(`${apiBaseUrl}/api/interviews/${sessionId}/skip`, 'POST')

export const endInterviewSession = (apiBaseUrl: string, sessionId: string) =>
  request<InterviewSessionStateResponse>(`${apiBaseUrl}/api/interviews/${sessionId}/end`, 'POST')

export const generateInterviewReply = (apiBaseUrl: string, inputText: string) =>
  request<InterviewReplyResponse>(
    `${apiBaseUrl}/api/interviews/reply-preview`,
    'POST',
    { inputText },
    { 'Content-Type': 'application/json' },
  )

export const listInterviewSessions = (apiBaseUrl: string) =>
  request<InterviewSessionSummaryListResponse>(`${apiBaseUrl}/api/interviews`, 'GET')

export const getInterviewReport = (apiBaseUrl: string, sessionId: string) =>
  request<InterviewReportResponse>(`${apiBaseUrl}/api/interviews/${sessionId}/report`, 'GET')

export const previewResumePlan = (apiBaseUrl: string, payload: ResumePreviewRequest) =>
  request<ResumePreviewResponse>(
    `${apiBaseUrl}/api/interviews/resume-preview`,
    'POST',
    payload,
    { 'Content-Type': 'application/json' },
  )
