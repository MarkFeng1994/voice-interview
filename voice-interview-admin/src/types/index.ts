export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface UserProfile {
  id: string
  username: string
  nickname: string
}

export interface LoginPayload {
  token: string
  expiresIn: number
  profile: UserProfile
}

export interface CategoryItem {
  id: string
  userId: string
  name: string
  parentId: string
  sortOrder: number
}

export interface QuestionItem {
  id: string
  userId: string
  categoryId: string
  title: string
  content: string
  answer: string | null
  difficulty: number
  source: string | null
  sourceUrl: string | null
  tags: string[]
}

export interface ImportTaskItem {
  id: string
  userId: string
  type: string
  categoryId: string
  fileName: string | null
  sourceUrl: string | null
  status: string
  totalCount: number
  successCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface InterviewSessionSummary {
  sessionId: string
  status: string
  title: string
  startedAt: string | null
  lastUpdatedAt: string | null
  totalQuestions: number
  answeredRounds: number
  overallScore: number | null
  summary: string
  stage?: string
  durationMinutes?: number
}

export interface InterviewQuestionReport {
  questionIndex: number
  title: string
  prompt: string
  score: number | null
  summary: string
}

export interface InterviewReport {
  sessionId: string
  status: string
  title: string
  overallScore: number | null
  overallComment: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  questionReports: InterviewQuestionReport[]
}

export interface ProviderMetricItem {
  capability: string
  provider: string
  totalCalls: number
  successCalls: number
  failureCalls: number
  averageLatencyMs: number
  lastLatencyMs: number
  lastCalledAt: string | null
  lastSuccessAt: string | null
  lastFailureAt: string | null
  lastError: string | null
}

export interface ImportTextPayload {
  categoryId: string
  rawText: string
  fileName: string
}

export interface ImportTextResult {
  taskId: string
  totalCount: number
  successCount: number
  message: string
}
