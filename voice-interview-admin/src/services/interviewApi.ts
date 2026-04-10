import type { InterviewSessionSummary, InterviewReport } from '@/types'
import { request } from './http'

export const getSessions = () => request<InterviewSessionSummary[]>('/api/interviews')

export const getReport = (sessionId: string) =>
  request<InterviewReport>(`/api/interviews/${sessionId}/report`)
