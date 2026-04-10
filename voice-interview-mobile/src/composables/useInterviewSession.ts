import { computed, ref } from 'vue'

import {
  endInterviewSession,
  generateInterviewReply,
  getInterviewSessionState,
  skipInterviewQuestion,
  startInterviewSession,
  submitInterviewAnswer,
} from '@/services/interviewApi'
import type {
  StartInterviewRequest,
  InterviewSessionMessage,
  InterviewSessionState,
  InterviewMessage,
} from '@/types/interview'
import { formatDurationMs } from '@/utils/time'

interface UseInterviewSessionOptions {
  apiBaseUrl: string
  initialPreviewMessages: InterviewMessage[]
  initialQuestionTitle: string
  initialQuestionIndex: number
  initialTotalQuestions: number
  initialMaxFollowUpPerQuestion: number
  initialTextPrompt: string
  toAbsoluteUrl: (value: string) => string
}

const mapSessionMessage = (
  message: InterviewSessionMessage,
  toAbsoluteUrl: (value: string) => string,
): InterviewMessage => ({
  id: Number.parseInt(message.id.replace(/-/g, '').slice(0, 12), 16) || Date.now(),
  role: message.role,
  speaker: message.speaker,
  text: message.text,
  audioMeta: message.audioDurationMs ? formatDurationMs(message.audioDurationMs) : undefined,
  audioUrl: message.audioUrl ? toAbsoluteUrl(message.audioUrl) : undefined,
})

export const useInterviewSession = (options: UseInterviewSessionOptions) => {
  const messages = ref<InterviewMessage[]>([...options.initialPreviewMessages])
  const sessionStatus = ref('PREVIEW')
  const currentQuestionTitle = ref(options.initialQuestionTitle)
  const currentQuestionPrompt = ref('')
  const currentQuestionIndex = ref(options.initialQuestionIndex)
  const totalQuestions = ref(options.initialTotalQuestions)
  const followUpIndex = ref(0)
  const maxFollowUpPerQuestion = ref(options.initialMaxFollowUpPerQuestion)
  const lastAiAudioUrl = ref('')
  const lastSessionId = ref('')
  const textPrompt = ref(options.initialTextPrompt)

  const progressLabel = computed(() =>
    sessionStatus.value === 'PREVIEW'
      ? '会话预览'
      : `第 ${currentQuestionIndex.value} / ${totalQuestions.value} 题`
  )

  const sessionStatusLabel = computed(() => {
    switch (sessionStatus.value) {
      case 'IN_PROGRESS':
        return '进行中'
      case 'COMPLETED':
        return '已完成'
      case 'CANCELLED':
        return '已结束'
      default:
        return '预览'
    }
  })

  const applySessionState = (session: InterviewSessionState) => {
    lastSessionId.value = session.sessionId
    sessionStatus.value = session.status
    currentQuestionIndex.value = session.currentQuestionIndex
    totalQuestions.value = session.totalQuestions
    followUpIndex.value = session.followUpIndex
    maxFollowUpPerQuestion.value = session.maxFollowUpPerQuestion
    currentQuestionTitle.value = session.currentQuestionTitle || options.initialQuestionTitle
    currentQuestionPrompt.value = session.currentQuestionPrompt || ''
    messages.value = session.messages.map((message) => mapSessionMessage(message, options.toAbsoluteUrl))

    const latestAiMessage = [...session.messages].reverse().find((message) => message.role === 'ai' && message.audioUrl)
    lastAiAudioUrl.value = latestAiMessage?.audioUrl ? options.toAbsoluteUrl(latestAiMessage.audioUrl) : ''
  }

  const applyIncomingSessionState = (session: InterviewSessionState) => {
    applySessionState(session)
  }

  const resetPreviewState = () => {
    sessionStatus.value = 'PREVIEW'
    currentQuestionTitle.value = options.initialQuestionTitle
    currentQuestionPrompt.value = ''
    currentQuestionIndex.value = options.initialQuestionIndex
    totalQuestions.value = options.initialTotalQuestions
    followUpIndex.value = 0
    maxFollowUpPerQuestion.value = options.initialMaxFollowUpPerQuestion
    lastAiAudioUrl.value = ''
    lastSessionId.value = ''
    messages.value = [...options.initialPreviewMessages]
  }

  const ensureSuccess = <T extends { success: boolean; message: string }>(payload: T, fallbackMessage: string) => {
    if (!payload.success) {
      throw new Error(payload.message || fallbackMessage)
    }
    return payload
  }

  const startSession = async (request?: StartInterviewRequest) => {
    const result = ensureSuccess(
      await startInterviewSession(options.apiBaseUrl, request),
      '启动练习失败',
    )
    applySessionState(result.data)
    textPrompt.value = ''
    return result.data
  }

  const submitLatestAnswer = async (fileId: string | null) => {
    if (!lastSessionId.value) {
      throw new Error('先开始一轮练习。')
    }

    const textAnswer = textPrompt.value.trim() || null
    if (!fileId && !textAnswer) {
      throw new Error('先上传音频，或在输入框里填一段文本。')
    }

    const payload = ensureSuccess(
      await submitInterviewAnswer(options.apiBaseUrl, {
        sessionId: lastSessionId.value,
        fileId,
        textAnswer,
      }),
      '提交回答失败',
    )
    applySessionState(payload.data)
    textPrompt.value = ''
    return payload.data
  }

  const refreshSessionState = async () => {
    if (!lastSessionId.value) {
      throw new Error('先开始一轮练习。')
    }

    const payload = ensureSuccess(
      await getInterviewSessionState(options.apiBaseUrl, lastSessionId.value),
      '同步状态失败',
    )
    applySessionState(payload.data)
    return payload.data
  }

  const restoreSession = async (sessionId: string) => {
    const payload = ensureSuccess(
      await getInterviewSessionState(options.apiBaseUrl, sessionId),
      '恢复会话失败',
    )
    applySessionState(payload.data)
    return payload.data
  }

  const skipQuestion = async () => {
    if (!lastSessionId.value) {
      throw new Error('先开始一轮练习。')
    }

    const payload = ensureSuccess(
      await skipInterviewQuestion(options.apiBaseUrl, lastSessionId.value),
      '跳题失败',
    )
    applySessionState(payload.data)
    return payload.data
  }

  const endSession = async () => {
    if (!lastSessionId.value) {
      throw new Error('先开始一轮练习。')
    }

    const payload = ensureSuccess(
      await endInterviewSession(options.apiBaseUrl, lastSessionId.value),
      '结束练习失败',
    )
    applySessionState(payload.data)
    return payload.data
  }

  const generateAiReply = async () => {
    const payload = ensureSuccess(
      await generateInterviewReply(options.apiBaseUrl, textPrompt.value),
      'AI 回复生成失败',
    )
    lastAiAudioUrl.value = options.toAbsoluteUrl(payload.data.audioUrl)
    messages.value.push({
      id: Date.now(),
      role: 'ai',
      speaker: 'AI 面试官',
      text: payload.data.spokenText,
      audioMeta: formatDurationMs(payload.data.audioDurationMs),
      audioUrl: lastAiAudioUrl.value,
    })
    return payload.data
  }

  const appendMessage = (message: InterviewMessage) => {
    messages.value.push(message)
  }

  return {
    messages,
    sessionStatus,
    currentQuestionTitle,
    currentQuestionPrompt,
    currentQuestionIndex,
    totalQuestions,
    followUpIndex,
    maxFollowUpPerQuestion,
    lastAiAudioUrl,
    lastSessionId,
    textPrompt,
    progressLabel,
    sessionStatusLabel,
    startSession,
    submitLatestAnswer,
    refreshSessionState,
    restoreSession,
    skipQuestion,
    endSession,
    generateAiReply,
    appendMessage,
    applyIncomingSessionState,
    resetPreviewState,
  }
}
