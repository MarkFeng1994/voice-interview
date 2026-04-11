export type MessageRole = 'ai' | 'user'

export interface InterviewMessage {
  id: number
  role: MessageRole
  speaker: string
  text: string
  audioMeta?: string
  audioUrl?: string
}

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export interface MediaUploadPayload {
  fileId: string
  fileName: string
  contentType: string
  size: number
  url: string
}

export type MediaUploadResponse = ApiEnvelope<MediaUploadPayload>

export interface ResumeProfilePayload {
  resumeId: string
  mediaFileId: string
  parseStatus: 'UPLOADED' | 'PARSING' | 'PARSED' | 'FAILED'
  resumeSummary: string | null
  extractedKeywords: string[]
  projectHighlights: string[]
  parseError: string | null
}

export type ResumeProfileResponse = ApiEnvelope<ResumeProfilePayload>

export interface AsrPayload {
  fileId: string
  provider: string
  transcript: string
  confidence: number
}

export type AsrResponse = ApiEnvelope<AsrPayload>

export interface InterviewReplyPayload {
  inputText: string
  spokenText: string
  decisionSuggestion: string
  scoreSuggestion: number | null
  audioFileId: string
  audioUrl: string
  audioDurationMs: number
}

export type InterviewReplyResponse = ApiEnvelope<InterviewReplyPayload>

export interface InterviewSessionMessage {
  id: string
  role: MessageRole
  speaker: string
  text: string
  roundType: string
  questionIndex: number
  followUpIndex: number
  audioUrl: string | null
  audioDurationMs: number
  scoreSuggestion: number | null
  answerMode: string | null
  createdAt: string
}

export interface InterviewQuestionSnapshot {
  questionIndex: number
  titleSnapshot: string
  promptSnapshot: string
}

export interface InterviewRoundRecord {
  roundId: string
  questionIndex: number
  followUpIndex: number
  roundType: string
  aiMessageText: string
  aiAudioUrl: string
  aiAudioDurationMs: number
  scoreSuggestion: number | null
  userAnswerText: string | null
  userAudioUrl: string | null
  userAnswerMode: string | null
  analysisReason?: string | null
  createdAt: string
  answeredAt: string | null
}

export interface InterviewSessionState {
  sessionId: string
  status: string
  stage: string
  durationMinutes: number
  currentQuestionIndex: number
  totalQuestions: number
  followUpIndex: number
  maxFollowUpPerQuestion: number
  currentQuestionTitle: string | null
  currentQuestionPrompt: string | null
  questions: InterviewQuestionSnapshot[]
  rounds: InterviewRoundRecord[]
  messages: InterviewSessionMessage[]
}

export type InterviewSessionStateResponse = ApiEnvelope<InterviewSessionState>

export interface InterviewWsTicketPayload {
  ticket: string
  sessionId: string
}

export type InterviewWsTicketResponse = ApiEnvelope<InterviewWsTicketPayload>

export interface InterviewSocketMessage {
  type: string
  session: InterviewSessionState | null
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
}

export type InterviewSessionSummaryListResponse = ApiEnvelope<InterviewSessionSummary[]>

export interface InterviewPreset {
  key: string
  title: string
  summary: string
  tags: string[]
  questionCount: number
}

export interface StartInterviewRequest {
  presetKey?: string
  resumeFileId?: string
  questionCount?: number
  durationMinutes?: number
  interviewerSpeakerId?: number
  interviewerSpeechSpeed?: number
}

export type InterviewPresetListResponse = ApiEnvelope<InterviewPreset[]>

export type ExplanationGeneratedBy = 'RULE' | 'RULE_PLUS_LLM'

export type InterviewExplanationLevel = 'STRONG' | 'MEDIUM' | 'WEAK'

export interface InterviewOverallExplanation {
  level: InterviewExplanationLevel | null
  summaryText: string
  evidencePoints: string[]
  improvementSuggestions: string[]
  generatedBy: ExplanationGeneratedBy
}

export interface InterviewQuestionExplanation {
  performanceLevel: InterviewExplanationLevel | null
  summaryText: string
  evidencePoints: string[]
  improvementSuggestion: string | null
  generatedBy: ExplanationGeneratedBy
}

export interface InterviewQuestionReport {
  questionIndex: number
  title: string
  prompt: string
  score: number | null
  summary: string
  explanation?: InterviewQuestionExplanation | null
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
  overallExplanation?: InterviewOverallExplanation | null
}

export type InterviewReportResponse = ApiEnvelope<InterviewReport>

export interface ResumeInterviewPlan {
  resumeFileId: string | null
  resumeSummary: string
  extractedKeywords: string[]
  matchedCategoryNames: string[]
  matchedLibraryQuestionTitles: string[]
  missingKeywords: string[]
  generatedQuestions: GeneratedResumeQuestionView[]
  questions: InterviewQuestionCardView[]
  usedPresetFallback: boolean
}

export interface GeneratedResumeQuestionView {
  title: string
  prompt: string
  targetKeyword: string | null
  difficulty: number
}

export interface InterviewQuestionCardView {
  title: string
  prompt: string
  sourceType: string
  sourceQuestionId: string | null
  sourceCategoryId: string | null
  difficulty: number | null
}

export interface ResumePreviewRequest {
  resumeFileId: string
  presetKey?: string
  questionCount?: number
}

export type ResumePreviewResponse = ApiEnvelope<ResumeInterviewPlan>

export interface ProviderCapabilityStatus {
  provider: string
  status: string
  message: string
  details: Record<string, unknown>
}

export interface ProviderRuntimeSummary {
  ai: ProviderCapabilityStatus
  asr: ProviderCapabilityStatus
  tts: ProviderCapabilityStatus
}

export type ProviderRuntimeResponse = ApiEnvelope<ProviderRuntimeSummary>

export interface TtsPreviewPayload {
  text: string
  fileId: string
  audioUrl: string
  audioDurationMs: number
}

export type TtsPreviewResponse = ApiEnvelope<TtsPreviewPayload>
