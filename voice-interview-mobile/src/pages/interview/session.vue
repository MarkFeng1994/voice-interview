<template>
  <view class="booth-screen">
    <view class="ambient-blur blur-a" />
    <view class="ambient-blur blur-b" />

    <view class="stage-card">
      <view class="stage-head">
        <view class="stage-copy">
          <text class="eyebrow">Interview Booth</text>
          <text class="stage-title">{{ currentQuestionTitle }}</text>
          <text class="stage-subtitle">{{ stagePrompt }}</text>
        </view>
        <view class="stage-status">
          <text class="status-chip" :class="sessionStatus.toLowerCase()">{{ sessionStatusLabel }}</text>
          <text class="status-progress">{{ progressLabel }}</text>
        </view>
      </view>

      <view class="presence-row">
        <view class="presence-card">
          <view class="presence-orb-shell">
            <view class="presence-orb">AI</view>
          </view>
          <text class="presence-name">{{ voiceToneLabel }}</text>
          <text class="presence-note">当前面试官音色</text>
        </view>

        <view class="telemetry-board">
          <view class="telemetry-card">
            <text class="telemetry-label">Preset</text>
            <text class="telemetry-value">{{ selectedPresetTitle || '自由练习' }}</text>
          </view>
          <view class="telemetry-card">
            <text class="telemetry-label">Stage</text>
            <text class="telemetry-value">{{ stageLabel }}</text>
          </view>
          <view class="telemetry-card">
            <text class="telemetry-label">Mode</text>
            <text class="telemetry-value">{{ preferredAnswerModeLabel }}</text>
          </view>
          <view class="telemetry-card">
            <text class="telemetry-label">Follow-up</text>
            <text class="telemetry-value">{{ followUpIndex }} / {{ maxFollowUpPerQuestion }}</text>
          </view>
          <view class="telemetry-card">
            <text class="telemetry-label">Duration</text>
            <text class="telemetry-value">{{ durationLabel }}</text>
          </view>
          <view class="telemetry-card live">
            <view class="live-copy">
              <text class="telemetry-label">Realtime Link</text>
              <text class="live-value" :class="socketStatus.toLowerCase()">{{ socketStatusLabel }}</text>
            </view>
            <view class="live-bars">
              <view
                v-for="height in monitorHeights"
                :key="height"
                class="live-bar"
                :style="{ height: `${height}rpx` }"
              />
            </view>
          </view>
        </view>
      </view>
    </view>

    <scroll-view
      class="dialogue-stream"
      scroll-y
      scroll-with-animation
      :scroll-into-view="scrollAnchorId"
    >
      <view class="stream-status-card">
        <view class="stream-status-head">
          <text class="stream-status-label">Current State</text>
          <text class="stream-status-value">{{ statusText }}</text>
        </view>
        <text class="stream-status-copy">{{ hintText }}</text>
        <text v-if="mediaDraftLabel" class="draft-pill">{{ mediaDraftLabel }}</text>
        <view v-if="showStatusActions" class="status-actions">
          <button
            v-if="showRetryRestoreAction"
            class="inline-action-btn"
            :disabled="isRestoring"
            @click="retryRestoreSession"
          >
            {{ isRestoring ? '恢复中...' : '重试恢复' }}
          </button>
          <button
            v-if="showRealtimeRecoveryActions"
            class="inline-action-btn"
            :disabled="isReconnecting"
            @click="reconnectRealtime"
          >
            {{ isReconnecting ? '重连中...' : '重连实时' }}
          </button>
          <button
            v-if="showRealtimeRecoveryActions"
            class="inline-action-btn secondary"
            :disabled="isRefreshing"
            @click="refreshInterviewState"
          >
            {{ isRefreshing ? '同步中...' : '手动同步' }}
          </button>
          <button
            v-if="showRetryRestoreAction"
            class="inline-action-btn secondary"
            @click="goToHistory"
          >
            返回历史
          </button>
        </view>
      </view>

      <view
        v-for="message in messages"
        :key="message.id"
        :id="`message-${message.id}`"
        class="message-row"
        :class="message.role"
      >
        <view class="message-avatar" :class="message.role">
          {{ message.role === 'ai' ? 'AI' : '我' }}
        </view>
        <view class="message-stack">
          <text class="message-speaker">{{ message.speaker }}</text>
          <view class="message-bubble" :class="message.role">
            <text class="message-text">{{ message.text }}</text>
          </view>
          <view
            v-if="message.audioUrl"
            class="audio-pill interactive"
            @click="playMessageAudio(message)"
          >
            <text class="audio-label">
              {{ message.role === 'user'
                ? `回放我的语音${message.audioMeta ? ` ${message.audioMeta}` : ''}`
                : `播放题目音频${message.audioMeta ? ` ${message.audioMeta}` : ''}` }}
            </text>
          </view>
          <view v-else-if="message.audioMeta" class="audio-pill">
            <text class="audio-label">音频 {{ message.audioMeta }}</text>
          </view>
        </view>
      </view>
      <view id="stream-anchor-bottom" class="stream-anchor" />
    </scroll-view>

    <view class="dock-card">
      <view class="dock-head">
        <view class="dock-copy">
          <text class="dock-title">{{ composerTitle }}</text>
          <text class="dock-note">{{ composerNote }}</text>
        </view>
        <text class="signal-tag">{{ socketStatusLabel }}</text>
      </view>

      <textarea
        v-model="textPrompt"
        class="composer-input"
        :class="{ disabled: isComposerDisabled }"
        maxlength="400"
        auto-height
        :disabled="isComposerDisabled"
        :placeholder="composerPlaceholder"
      />

      <view class="dock-primary">
        <button
          v-if="canStartFromPreview"
          class="primary-btn"
          :disabled="isPrimaryButtonDisabled"
          @click="startInterviewSession"
        >
          {{ startButtonLabel }}
        </button>
        <button
          v-else-if="!isRestoreFailed"
          class="primary-btn"
          :disabled="isPrimaryButtonDisabled"
          @click="submitLatestAnswer"
        >
          {{ submitButtonLabel }}
        </button>
        <button class="secondary-btn" :disabled="isSecondaryButtonDisabled" @click="playLastAiAudio">
          {{ lastAiAudioUrl ? '播放题目音频' : '暂无题目音频' }}
        </button>
      </view>

      <view class="voice-station">
        <WaveformVisualizer
          :active="isRecording"
          :amplitudes="waveform.amplitudes.value"
        />
        <button
          class="record-btn"
          :disabled="isRecordButtonDisabled"
          @touchstart.prevent="startRecording"
          @touchend.prevent="stopRecording"
          @touchcancel.prevent="stopRecording"
          @mousedown.prevent="startRecording"
          @mouseup.prevent="stopRecording"
        >
          <text class="record-btn-top">{{ recordButtonTopLabel }}</text>
          <text class="record-btn-main">{{ recordButtonMainLabel }}</text>
        </button>
        <view class="voice-tools">
          <button class="tool-btn" :disabled="areVoiceToolsDisabled" @click="pickAudioFile">选择音频</button>
          <button class="tool-btn" :disabled="areVoiceToolsDisabled" @click="transcribeLastAudio">重新识别</button>
          <button class="tool-btn" :disabled="areVoiceToolsDisabled" @click="clearDraft">清空草稿</button>
        </view>
      </view>

      <view v-if="showAuxActions" class="aux-actions">
        <button class="ghost-btn" :disabled="isRefreshing" @click="refreshInterviewState">{{ isRefreshing ? '同步中' : '同步' }}</button>
        <button class="ghost-btn" :disabled="isReconnecting" @click="reconnectRealtime">{{ isReconnecting ? '重连中' : '重连' }}</button>
        <button class="ghost-btn" :disabled="isSkipping || isSessionBusy" @click="skipInterviewQuestion">{{ isSkipping ? '跳过中' : '跳过' }}</button>
        <button class="ghost-btn" :disabled="isSessionBusy" @click="goToReport">报告</button>
        <button class="ghost-btn danger" :disabled="isEnding || isSessionBusy" @click="endInterviewSession">{{ isEnding ? '结束中' : '结束' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL, toAbsoluteUrl } from '@/config/api'
import { useAudioPlayback } from '@/composables/useAudioPlayback'
import { useInterviewSession } from '@/composables/useInterviewSession'
import { useMediaUpload } from '@/composables/useMediaUpload'
import { useWaveform } from '@/composables/useWaveform'
import { useInterviewSocket } from '@/composables/useInterviewSocket'
import { useUserStore } from '@/stores/user'
import WaveformVisualizer from '@/components/voice/WaveformVisualizer.vue'
import type { InterviewMessage } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'
import { readInterviewVoiceSettings } from '@/utils/interviewSettings'
import { formatDurationMs } from '@/utils/time'

const previewMessages: InterviewMessage[] = [
  {
    id: 1,
    role: 'ai',
    speaker: 'AI 面试官',
    text: '点击下方“开始面试”后，我会按题目顺序和你做一轮对话式模拟面试。',
  },
]

const {
  messages,
  sessionStatus,
  currentStage,
  durationMinutes,
  currentQuestionTitle,
  currentQuestionPrompt,
  followUpIndex,
  maxFollowUpPerQuestion,
  lastAiAudioUrl,
  lastSessionId,
  textPrompt,
  progressLabel,
  sessionStatusLabel,
  startSession: startSessionRequest,
  submitLatestAnswer: submitLatestAnswerRequest,
  refreshSessionState: refreshSessionStateRequest,
  restoreSession: restoreSessionRequest,
  skipQuestion: skipQuestionRequest,
  endSession: endSessionRequest,
  applyIncomingSessionState,
} = useInterviewSession({
  apiBaseUrl: API_BASE_URL,
  initialPreviewMessages: previewMessages,
  initialStage: 'OPENING',
  initialDurationMinutes: 60,
  initialQuestionTitle: '开始一轮模拟面试',
  initialQuestionIndex: 0,
  initialTotalQuestions: 3,
  initialMaxFollowUpPerQuestion: 1,
  initialTextPrompt: '',
  toAbsoluteUrl,
})

const statusText = ref('待开始')
const hintText = ref('点击“开始面试”后，可以直接文字作答，或按住说话上传语音。')
const selectedPresetKey = ref('')
const selectedPresetTitle = ref('')
const preferredAnswerMode = ref<'VOICE' | 'TEXT'>('VOICE')
const userStore = useUserStore()
const voiceSettings = readInterviewVoiceSettings()
const resumeFileId = ref('')
const resumeQuestionCount = ref(0)
const plannedDurationMinutes = ref(60)
const restoredSessionId = ref('')
const scrollAnchorId = ref('stream-anchor-bottom')
const isRestoring = ref(false)
const isRestoreFailed = ref(false)
const isStarting = ref(false)
const isSubmitting = ref(false)
const isRefreshing = ref(false)
const isReconnecting = ref(false)
const isSkipping = ref(false)
const isEnding = ref(false)

const voiceToneMap: Record<number, string> = {
  33: 'Cherry',
  34: 'Serena',
  35: 'Ethan',
  36: 'Chelsie',
}

const monitorHeights = [18, 34, 24, 42, 16, 36, 22, 30]

const waveform = useWaveform()

const preferredAnswerModeLabel = computed(() =>
  preferredAnswerMode.value === 'TEXT' ? '文字优先' : '语音优先',
)

const voiceToneLabel = computed(() =>
  voiceToneMap[Number(voiceSettings.interviewerSpeakerId)] || `Voice ${voiceSettings.interviewerSpeakerId}`,
)

const stageLabel = computed(() => {
  const map: Record<string, string> = {
    OPENING: '开场',
    JAVA_CORE: 'Java 基础',
    PROJECT_DEEP_DIVE: '项目深挖',
    WRAP_UP: '总结',
  }
  return map[currentStage.value] || '面试中'
})

const durationLabel = computed(() => `${durationMinutes.value || plannedDurationMinutes.value} 分钟`)

const socketStatusLabel = computed(() => {
  switch (socketStatus.value) {
    case 'OPEN':
      return 'LIVE'
    case 'CONNECTING':
      return 'LINKING'
    case 'CLOSED':
      return 'OFFLINE'
    default:
      return socketStatus.value || 'IDLE'
  }
})

const stagePrompt = computed(() => {
  if (currentQuestionPrompt.value) {
    return currentQuestionPrompt.value
  }
  if (sessionStatus.value === 'PREVIEW') {
    return '现在还在开场阶段。开始后，AI 会先抛出第一道题，再根据你的回答决定是否继续追问。'
  }
  return '当前题干正在同步，请继续等待实时状态或直接查看下方对话记录。'
})

const composerTitle = computed(() =>
  sessionStatus.value === 'PREVIEW'
    ? '开场准备'
    : lastUploadedFileId.value
      ? '确认这次回答'
      : '继续作答',
)

const composerNote = computed(() => {
  if (sessionStatus.value === 'PREVIEW') {
    return '开始后你可以像打语音一样直接回答，也可以先用文字整理思路。'
  }
  if (lastUploadedFileId.value && lastTranscript.value) {
    return '转写已经自动填入输入框，发送前可以手动修正。'
  }
  if (lastUploadedFileId.value) {
    return '语音草稿已经准备好，你可以重新识别，或直接发送。'
  }
  return '按住说话会模拟真实面试节奏，文字输入更适合先整理结构。'
})

const composerPlaceholder = computed(() =>
  sessionStatus.value === 'PREVIEW'
    ? '先点击“开始面试”，随后可以在这里直接文字作答。'
    : '语音识别结果会自动填到这里，你也可以手动修改后再发送。',
)

const canStartFromPreview = computed(() =>
  sessionStatus.value === 'PREVIEW' && !restoredSessionId.value,
)

const isSessionBusy = computed(() =>
  isRestoring.value || isStarting.value || isSubmitting.value || isSkipping.value || isEnding.value,
)

const isComposerDisabled = computed(() =>
  isRestoring.value || isStarting.value || isEnding.value,
)

const hasDraftAnswer = computed(() => Boolean(lastUploadedFileId.value || textPrompt.value.trim()))

const isPrimaryButtonDisabled = computed(() => {
  if (canStartFromPreview.value) {
    return isStarting.value || isRestoring.value
  }
  return isSubmitting.value || isUploading.value || isEnding.value || isSkipping.value || !hasDraftAnswer.value
})

const isSecondaryButtonDisabled = computed(() =>
  !lastAiAudioUrl.value || isStarting.value || isEnding.value,
)

const isRecordButtonDisabled = computed(() =>
  isComposerDisabled.value || isUploading.value || isSubmitting.value || isReconnecting.value,
)

const areVoiceToolsDisabled = computed(() =>
  isRecordButtonDisabled.value || isSkipping.value || isEnding.value,
)

const startButtonLabel = computed(() => (isStarting.value ? '启动中...' : '开始面试'))

const submitButtonLabel = computed(() => (isSubmitting.value ? '发送中...' : '发送回答'))

const recordButtonTopLabel = computed(() => {
  if (isUploading.value) {
    return 'Uploading'
  }
  return isRecording.value ? 'Recording' : 'Push To Talk'
})

const recordButtonMainLabel = computed(() => {
  if (isUploading.value) {
    return '语音上传中'
  }
  return isRecording.value ? '松开录音并识别' : '按住说话'
})

const showRetryRestoreAction = computed(() => isRestoreFailed.value)

const showRealtimeRecoveryActions = computed(() =>
  (socketStatus.value === 'ERROR' || socketStatus.value === 'CLOSED') && sessionStatus.value !== 'PREVIEW',
)

const showStatusActions = computed(() =>
  showRetryRestoreAction.value || showRealtimeRecoveryActions.value,
)

const showAuxActions = computed(() =>
  sessionStatus.value !== 'PREVIEW' && !isRestoreFailed.value,
)

const setUiStatus = (status: string, hint: string) => {
  statusText.value = status
  hintText.value = hint
}

const scrollToLatestMessage = async () => {
  scrollAnchorId.value = ''
  await nextTick()
  scrollAnchorId.value = 'stream-anchor-bottom'
}

const handleVoiceFailure = () => {
  preferredAnswerMode.value = 'TEXT'
  setUiStatus('语音失败', '已自动切换为文本作答')
}

const handleMediaStatusChange = (status: string, hint: string) => {
  if (['录音失败', '无法开始录音', '上传失败', '识别失败'].includes(status)) {
    handleVoiceFailure()
    return
  }
  setUiStatus(status, hint)
}

const { playAudio, stopAudio } = useAudioPlayback({
  onPlay: () => {
    setUiStatus('播放中', lastAiAudioUrl.value ? '正在播放当前题目的音频。' : latestAudioLabel.value)
  },
  onEnded: () => {
    setUiStatus('等待你的回答', mediaDraftLabel.value || '可以继续语音或文字回答。')
  },
  onError: (message) => {
    setUiStatus('播放失败', message)
  },
})

const {
  socketStatus,
  connect: connectInterviewSocket,
  disconnect: disconnectInterviewSocket,
  reconnect: reconnectInterviewSocket,
} = useInterviewSocket({
  apiBaseUrl: API_BASE_URL,
  onSessionMessage: (session, eventType) => {
    applyIncomingSessionState(session)
    if (session.status === 'COMPLETED' || session.status === 'CANCELLED') {
      goToReport(session.sessionId)
      return
    }
    if (eventType === 'SESSION_UPDATED') {
      setUiStatus('实时已更新', '当前会话已通过 WebSocket 自动同步。')
    }
  },
  onStatusChange: setUiStatus,
})

const {
  isRecording,
  isUploading,
  lastUploadedFileId,
  lastTranscript,
  lastAudioUrl,
  lastAudioDurationMs,
  latestAudioLabel,
  startRecording,
  stopRecording,
  pickAudioFile,
  transcribeLastAudio,
  resetMediaState,
} = useMediaUpload({
  apiBaseUrl: API_BASE_URL,
  toAbsoluteUrl,
  onStatusChange: handleMediaStatusChange,
  appendMessage: () => {
    // Upload now behaves as a draft step; messages are appended after real submit.
  },
  onTranscriptReady: (transcript) => {
    textPrompt.value = transcript
  },
  onAudioSamples: (samples) => {
    waveform.pushSamples(samples)
  },
})

watch(isRecording, (recording) => {
  if (recording) {
    waveform.start()
  } else {
    waveform.reset()
  }
})

watch(
  () => messages.value.length,
  () => {
    void scrollToLatestMessage()
  },
)

const mediaDraftLabel = computed(() => {
  if (lastUploadedFileId.value && lastTranscript.value) {
    return '语音已自动转写到发送框，可手动修改后再发送。'
  }
  if (lastUploadedFileId.value) {
    return '语音草稿已准备好，可以重新识别或直接发送。'
  }
  return ''
})

const restoreInterviewSession = async (sessionId: string) => {
  isRestoring.value = true
  isRestoreFailed.value = false
  setUiStatus('恢复中', '正在恢复你上一次的面试会话。')

  try {
    const restored = await restoreSessionRequest(sessionId)
    if (restored.status === 'COMPLETED' || restored.status === 'CANCELLED') {
      setUiStatus('面试已结束', '当前会话已经结束，正在为你打开原报告。')
      goToReport(restored.sessionId)
      return
    }
    setUiStatus('已恢复会话', '当前题目和历史消息已恢复，正在重连实时链路。')
    await connectInterviewSocket(sessionId)
    setUiStatus('已恢复练习', '你可以继续回答、跳过当前题，或直接结束本轮面试。')
  } catch (error) {
    isRestoreFailed.value = true
    setUiStatus(
      '恢复失败',
      error instanceof Error ? `${error.message}，可返回历史页重新选择。` : '无法恢复上一次练习，请重新开始。',
    )
  } finally {
    isRestoring.value = false
  }
}

onLoad(async (query) => {
  const redirect = typeof query?.sessionId === 'string'
    ? `/pages/interview/session?sessionId=${query.sessionId}`
    : '/pages/interview/session'
  if (!ensureAuthenticated(userStore, redirect)) {
    return
  }

  if (typeof query?.sessionId === 'string' && query.sessionId) {
    restoredSessionId.value = query.sessionId
    await restoreInterviewSession(query.sessionId)
    return
  }

  selectedPresetKey.value = typeof query?.presetKey === 'string' ? decodeURIComponent(query.presetKey) : ''
  selectedPresetTitle.value = typeof query?.presetTitle === 'string' ? decodeURIComponent(query.presetTitle) : ''
  preferredAnswerMode.value = query?.answerMode === 'TEXT' ? 'TEXT' : 'VOICE'
  resumeFileId.value = typeof query?.resumeFileId === 'string' ? query.resumeFileId : ''
  resumeQuestionCount.value = typeof query?.questionCount === 'string' ? parseInt(query.questionCount, 10) || 0 : 0
  plannedDurationMinutes.value = typeof query?.durationMinutes === 'string' ? parseInt(query.durationMinutes, 10) || 60 : 60
  if (selectedPresetTitle.value) {
    currentQuestionTitle.value = `${selectedPresetTitle.value} · 准备开始`
  }
  hintText.value = preferredAnswerMode.value === 'TEXT'
    ? '这轮默认更适合先用文字整理回答，之后也可以再切语音。'
    : '这轮默认更适合按住说话，模拟真实面试节奏。'
})

const startInterviewSession = async () => {
  isStarting.value = true
  stopAudio()
  setUiStatus('启动中', '正在生成首题和音频。')

  try {
    const session = await startSessionRequest({
      presetKey: selectedPresetKey.value || undefined,
      resumeFileId: resumeFileId.value || undefined,
      questionCount: resumeQuestionCount.value || undefined,
      durationMinutes: plannedDurationMinutes.value,
      interviewerSpeakerId: voiceSettings.interviewerSpeakerId,
      interviewerSpeechSpeed: voiceSettings.interviewerSpeechSpeed,
    })
    await connectInterviewSocket(session.sessionId)
    resetMediaState()
    setUiStatus('面试已开始', '先听题目，再直接文字作答，或按住说话上传语音。')
  } catch (error) {
    setUiStatus(
      '启动失败',
      error instanceof Error ? error.message : '请检查后端服务和网络连接。',
    )
  } finally {
    isStarting.value = false
  }
}

const submitLatestAnswer = async () => {
  if (!lastUploadedFileId.value && !textPrompt.value.trim()) {
    setUiStatus('没有可发送内容', '先输入一段回答，或按住说话上传语音。')
    return
  }

  isSubmitting.value = true
  setUiStatus('发送中', '正在提交回答并等待 AI 继续追问。')

  try {
    const usedVoiceDraft = Boolean(lastUploadedFileId.value)
    const voiceDraftUrl = lastAudioUrl.value
    const voiceDraftDuration = lastAudioDurationMs.value
    const session = await submitLatestAnswerRequest(lastUploadedFileId.value || null)
    resetMediaState()

    if (usedVoiceDraft && voiceDraftUrl) {
      const latestUserMessage = [...messages.value].reverse().find((item) => item.role === 'user')
      if (latestUserMessage) {
        latestUserMessage.audioUrl = voiceDraftUrl
        latestUserMessage.audioMeta = voiceDraftDuration ? formatDurationMs(voiceDraftDuration) : latestUserMessage.audioMeta
      }
    }

    if (session.status === 'IN_PROGRESS') {
      setUiStatus('已收到下一轮提问', '可以继续回答，也可以播放题目音频再组织答案。')
      return
    }

    setUiStatus('本轮已结束', '报告已经生成，你可以直接查看本轮总结。')
    goToReport(session.sessionId)
  } catch (error) {
    setUiStatus(
      '发送失败',
      error instanceof Error ? error.message : '请检查后端服务和网络连接。',
    )
  } finally {
    isSubmitting.value = false
  }
}

const refreshInterviewState = async () => {
  isRefreshing.value = true
  setUiStatus('同步中', '正在从后端拉取最新会话状态。')

  try {
    await refreshSessionStateRequest()
    setUiStatus('已同步', '页面状态已经和服务端保持一致。')
  } catch (error) {
    setUiStatus(
      '同步失败',
      error instanceof Error ? error.message : '请检查后端服务和网络连接。',
    )
  } finally {
    isRefreshing.value = false
  }
}

const reconnectRealtime = async () => {
  const sessionId = lastSessionId.value || restoredSessionId.value
  if (!sessionId) {
    setUiStatus('没有活动会话', '先开始或恢复一轮面试，再重连实时链路。')
    return
  }

  isReconnecting.value = true
  setUiStatus('重连中', '正在重新建立实时链路。')

  try {
    await reconnectInterviewSocket(sessionId)
    setUiStatus('实时链路已恢复', '会话更新会继续自动推送到当前页面。')
  } catch (error) {
    setUiStatus(
      '重连失败',
      error instanceof Error ? `${error.message}，你也可以直接文本作答。` : '实时链路连接失败，可继续文本作答。',
    )
  } finally {
    isReconnecting.value = false
  }
}

const skipInterviewQuestion = async () => {
  isSkipping.value = true
  setUiStatus('跳过中', '正在请求跳过当前题目。')

  try {
    const session = await skipQuestionRequest()
    resetMediaState()
    if (session.status === 'IN_PROGRESS') {
      setUiStatus('已进入下一题', '当前题目已经跳过，可以继续作答。')
      return
    }

    setUiStatus('面试已结束', '最后一题跳过后，本轮练习已经结束。')
    goToReport(session.sessionId)
  } catch (error) {
    setUiStatus(
      '跳过失败',
      error instanceof Error ? error.message : '请检查后端服务和网络连接。',
    )
  } finally {
    isSkipping.value = false
  }
}

const playLastAiAudio = () => {
  if (!lastAiAudioUrl.value) {
    setUiStatus('暂无题目音频', '当前这一步没有可播放的题目音频。')
    return
  }

  playAudio(lastAiAudioUrl.value)
}

const playMessageAudio = (message: InterviewMessage) => {
  if (message.audioUrl) {
    playAudio(message.audioUrl)
  }
}

const endInterviewSession = async () => {
  if (sessionStatus.value === 'PREVIEW') {
    setUiStatus('没有活动会话', '先开始一轮面试，再进行结束操作。')
    return
  }

  const confirmResult = await uni.showModal({
    title: '确认结束本轮面试？',
    content: '结束后将生成并跳转到本轮报告，当前会话不会继续追问。',
    confirmText: '确认结束',
    cancelText: '继续作答',
  })
  if (!confirmResult.confirm) {
    setUiStatus('继续作答', '当前面试未结束，你可以继续回答或稍后再结束。')
    return
  }

  isEnding.value = true
  setUiStatus('结束中', '正在结束当前面试并生成结果。')

  try {
    const session = await endSessionRequest()
    resetMediaState()
    setUiStatus('面试已结束', '当前会话已结束，可以查看本轮报告。')
    goToReport(session.sessionId)
  } catch (error) {
    setUiStatus(
      '结束失败',
      error instanceof Error ? error.message : '请检查后端服务和网络连接。',
    )
  } finally {
    isEnding.value = false
  }
}

const clearDraft = () => {
  textPrompt.value = ''
  resetMediaState()
  stopAudio()
  setUiStatus(
    sessionStatus.value === 'PREVIEW' ? '待开始' : '草稿已清空',
    sessionStatus.value === 'PREVIEW'
      ? '点击“开始面试”后，可以直接文字作答，或按住说话上传语音。'
      : '当前会话还在继续，你可以重新录音或重新输入这题答案。',
  )
}

const goToReport = (sessionId?: string | Event) => {
  const normalizedSessionId = typeof sessionId === 'string' ? sessionId : ''
  const finalSessionId = normalizedSessionId || lastSessionId.value || restoredSessionId.value
  if (!finalSessionId) {
    uni.showToast({
      title: '当前还没有可查看的报告',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  disconnectInterviewSocket()
  uni.navigateTo({
    url: `/pages/interview/report?sessionId=${finalSessionId}&source=session-end`,
  })
}

const retryRestoreSession = async () => {
  if (!restoredSessionId.value) {
    return
  }
  await restoreInterviewSession(restoredSessionId.value)
}

const goToHistory = () => {
  disconnectInterviewSocket()
  uni.navigateTo({
    url: '/pages/history/list',
  })
}
</script>

<style>
page {
  background:
    radial-gradient(circle at 0% 0%, rgba(255, 125, 66, 0.18), transparent 24%),
    radial-gradient(circle at 100% 12%, rgba(245, 171, 76, 0.16), transparent 26%),
    linear-gradient(180deg, #141821 0%, #0b0d12 58%, #090b10 100%);
  min-height: 100%;
}

.booth-screen {
  position: relative;
  min-height: 100vh;
  padding: 34rpx 24rpx 42rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 22rpx;
}

.ambient-blur {
  position: absolute;
  border-radius: 50%;
  filter: blur(24rpx);
  pointer-events: none;
}

.blur-a {
  top: 40rpx;
  right: -20rpx;
  width: 180rpx;
  height: 180rpx;
  background: rgba(255, 125, 66, 0.16);
}

.blur-b {
  top: 260rpx;
  left: -32rpx;
  width: 220rpx;
  height: 220rpx;
  background: rgba(245, 171, 76, 0.08);
}

.stage-card,
.dock-card,
.stream-status-card {
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(24, 29, 39, 0.94) 0%, rgba(11, 14, 20, 0.98) 100%);
  border: 2rpx solid var(--studio-line);
  box-shadow: var(--studio-shadow);
}

.stage-card,
.dock-card {
  padding: 30rpx;
  border-radius: 36rpx;
}

.stage-card::after,
.dock-card::after,
.stream-status-card::after {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 6rpx;
  background: linear-gradient(90deg, var(--studio-signal) 0%, rgba(245, 171, 76, 0) 72%);
}

.stage-head {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  align-items: flex-start;
}

.stage-copy {
  flex: 1;
  min-width: 0;
}

.eyebrow {
  display: block;
  font-size: 22rpx;
  letter-spacing: 3rpx;
  color: var(--studio-signal);
  text-transform: uppercase;
}

.stage-title {
  display: block;
  margin-top: 14rpx;
  font-family: var(--studio-font-display);
  font-size: 54rpx;
  line-height: 1.06;
  color: var(--studio-text);
}

.stage-subtitle {
  display: block;
  margin-top: 18rpx;
  font-size: 26rpx;
  line-height: 1.72;
  color: var(--studio-text-muted);
}

.stage-status {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  align-items: flex-end;
}

.status-chip,
.signal-tag,
.draft-pill,
.meta-pill {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.status-chip {
  background: rgba(255, 255, 255, 0.08);
  color: var(--studio-text);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.status-chip.in_progress {
  background: rgba(245, 171, 76, 0.18);
  color: var(--studio-signal-soft);
  border-color: rgba(245, 171, 76, 0.26);
}

.status-chip.completed {
  background: rgba(89, 199, 156, 0.18);
  color: var(--studio-success);
}

.status-chip.cancelled {
  background: rgba(255, 125, 115, 0.18);
  color: var(--studio-danger);
}

.status-progress {
  font-size: 22rpx;
  color: var(--studio-text-soft);
}

.presence-row {
  margin-top: 26rpx;
  display: grid;
  grid-template-columns: 236rpx minmax(0, 1fr);
  gap: 18rpx;
}

.presence-card,
.telemetry-card {
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 2rpx solid rgba(255, 255, 255, 0.06);
}

.presence-card {
  padding: 22rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.presence-orb-shell {
  width: 150rpx;
  height: 150rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, rgba(245, 171, 76, 0.2) 0%, rgba(245, 171, 76, 0.06) 62%, rgba(245, 171, 76, 0) 100%);
}

.presence-orb {
  width: 102rpx;
  height: 102rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--studio-signal) 0%, var(--studio-signal-strong) 100%);
  color: #25170d;
  font-size: 34rpx;
  font-weight: 700;
  animation: studioPulse 3.1s infinite;
}

.presence-name {
  margin-top: 18rpx;
  font-family: var(--studio-font-display);
  font-size: 32rpx;
  color: var(--studio-text);
}

.presence-note {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: var(--studio-text-soft);
}

.telemetry-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
}

.telemetry-card {
  min-height: 132rpx;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.telemetry-label,
.monitor-label {
  font-size: 20rpx;
  letter-spacing: 1.6rpx;
  color: var(--studio-text-soft);
  text-transform: uppercase;
}

.telemetry-value {
  display: block;
  margin-top: 10rpx;
  font-family: var(--studio-font-display);
  font-size: 30rpx;
  line-height: 1.2;
  color: var(--studio-text);
}

.telemetry-card.live {
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  gap: 14rpx;
}

.live-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.live-value {
  font-family: var(--studio-font-display);
  font-size: 30rpx;
  color: var(--studio-text);
}

.live-value.open {
  color: var(--studio-success);
}

.live-value.connecting {
  color: var(--studio-signal);
}

.live-value.closed {
  color: var(--studio-danger);
}

.live-bars {
  display: flex;
  align-items: flex-end;
  gap: 8rpx;
}

.live-bar {
  width: 10rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, var(--studio-signal-soft) 0%, var(--studio-signal-strong) 100%);
  opacity: 0.85;
}

.meta-row {
  margin-top: 18rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.meta-pill {
  background: rgba(245, 171, 76, 0.12);
  color: var(--studio-signal-soft);
  border: 1rpx solid rgba(245, 171, 76, 0.16);
}

.meta-pill.muted {
  background: rgba(255, 255, 255, 0.06);
  color: var(--studio-text-muted);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.dialogue-stream {
  flex: 1;
  min-height: 0;
  padding: 0 6rpx 4rpx;
}

.stream-status-card {
  margin-bottom: 18rpx;
  padding: 22rpx 22rpx 24rpx;
  border-radius: 28rpx;
}

.stream-status-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.stream-status-label {
  font-size: 20rpx;
  letter-spacing: 1.6rpx;
  color: var(--studio-text-soft);
  text-transform: uppercase;
}

.stream-status-value {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--studio-text);
}

.stream-status-copy {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--studio-text-muted);
}

.status-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.inline-action-btn {
  flex: 1 1 calc(50% - 6rpx);
  min-width: 180rpx;
  border: none;
  border-radius: 22rpx;
  padding: 14rpx 18rpx;
  font-size: 24rpx;
  background: rgba(245, 171, 76, 0.18);
  color: var(--studio-text);
  border: 1rpx solid rgba(245, 171, 76, 0.2);
}

.inline-action-btn.secondary {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.08);
}

.draft-pill {
  display: inline-block;
  margin-top: 16rpx;
  background: rgba(245, 171, 76, 0.12);
  color: var(--studio-signal-soft);
  border: 1rpx solid rgba(245, 171, 76, 0.16);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  margin-bottom: 24rpx;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}

.message-avatar.ai {
  background: rgba(245, 171, 76, 0.16);
  color: var(--studio-signal-soft);
  border: 1rpx solid rgba(245, 171, 76, 0.22);
}

.message-avatar.user {
  background: linear-gradient(135deg, var(--studio-signal) 0%, var(--studio-signal-strong) 100%);
  color: #25170d;
}

.message-stack {
  max-width: calc(100% - 98rpx);
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.message-row.user .message-stack {
  align-items: flex-end;
}

.message-speaker {
  font-size: 22rpx;
  color: var(--studio-text-soft);
}

.message-bubble {
  padding: 22rpx 24rpx;
  border-radius: 28rpx;
  box-shadow: 0 14rpx 36rpx rgba(0, 0, 0, 0.2);
}

.message-bubble.ai {
  background: rgba(255, 255, 255, 0.05);
  border-top-left-radius: 10rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.06);
}

.message-bubble.user {
  background: linear-gradient(135deg, var(--studio-signal) 0%, var(--studio-signal-strong) 100%);
  border-top-right-radius: 10rpx;
}

.message-text {
  font-size: 29rpx;
  line-height: 1.62;
  color: var(--studio-text);
}

.message-bubble.user .message-text {
  color: #25170d;
  font-weight: 600;
}

.audio-pill {
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

.audio-pill.interactive {
  background: rgba(245, 171, 76, 0.18);
  border-color: rgba(245, 171, 76, 0.24);
}

.audio-label {
  font-size: 22rpx;
  color: var(--studio-text-muted);
}

.dock-card::before {
  content: '';
  position: absolute;
  inset: auto -20rpx -46rpx auto;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(245, 171, 76, 0.12) 0%, rgba(245, 171, 76, 0) 70%);
  pointer-events: none;
}

.dock-head {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  align-items: flex-start;
}

.dock-copy {
  flex: 1;
  min-width: 0;
}

.dock-title {
  display: block;
  font-family: var(--studio-font-display);
  font-size: 34rpx;
  color: var(--studio-text);
}

.dock-note {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--studio-text-muted);
}

.signal-tag {
  background: rgba(255, 255, 255, 0.08);
  color: var(--studio-text);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.composer-input {
  width: 100%;
  min-height: 120rpx;
  margin-top: 18rpx;
  padding: 24rpx 22rpx;
  box-sizing: border-box;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  color: var(--studio-text);
  font-size: 28rpx;
  line-height: 1.62;
  border: 2rpx solid rgba(255, 255, 255, 0.08);
}

.composer-input.disabled {
  opacity: 0.72;
}

.dock-primary,
.aux-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 18rpx;
}

.voice-station {
  margin-top: 20rpx;
  padding: 18rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.record-btn {
  width: 100%;
  border: none;
  border-radius: 28rpx;
  padding: 22rpx 20rpx;
  background: linear-gradient(135deg, rgba(245, 171, 76, 0.2) 0%, rgba(255, 125, 66, 0.24) 100%);
  border: 2rpx solid rgba(245, 171, 76, 0.18);
  color: var(--studio-text);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  align-items: center;
}

.record-btn-top {
  font-size: 20rpx;
  letter-spacing: 1.8rpx;
  text-transform: uppercase;
  color: var(--studio-signal-soft);
}

.record-btn-main {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--studio-text);
}

.voice-tools {
  margin-top: 14rpx;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
}

.tool-btn,
.secondary-btn,
.ghost-btn,
.primary-btn,
.inline-action-btn {
  border: none;
  border-radius: 24rpx;
  font-size: 26rpx;
}

.tool-btn,
.secondary-btn {
  background: rgba(255, 255, 255, 0.08);
  color: var(--studio-text);
  border: 2rpx solid rgba(255, 255, 255, 0.08);
}

.primary-btn {
  flex: 1 1 100%;
  background: linear-gradient(135deg, var(--studio-signal) 0%, var(--studio-signal-strong) 100%);
  color: #25170d;
  font-weight: 700;
}

.secondary-btn {
  flex: 1 1 calc(50% - 7rpx);
}

.ghost-btn {
  flex: 1 1 calc(25% - 11rpx);
  min-width: 140rpx;
  background: rgba(255, 255, 255, 0.06);
  color: var(--studio-text);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.ghost-btn.danger {
  color: var(--studio-danger);
}

.primary-btn[disabled],
.secondary-btn[disabled],
.ghost-btn[disabled],
.tool-btn[disabled],
.record-btn[disabled],
.inline-action-btn[disabled] {
  opacity: 0.55;
}

.stream-anchor {
  height: 2rpx;
}

@media (max-width: 720rpx) {
  .presence-row {
    grid-template-columns: 1fr;
  }

  .telemetry-board {
    grid-template-columns: 1fr;
  }
}
</style>
