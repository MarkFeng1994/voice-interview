<template>
  <view class="setup-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">Scene Select</text>
        <text class="hero-chip">Round Builder</text>
      </view>
      <text class="title">先定场景，再决定这一轮怎么开口。</text>
      <text class="subtitle">
        选择预设题组或上传简历，AI 会根据你的经历匹配面试题。
      </text>
    </view>

    <!-- Tab Switcher -->
    <view class="tab-bar" role="tablist">
      <view class="tab-item" :class="{ active: activeTab === 'preset' }" role="tab" :aria-selected="activeTab === 'preset'" @click="activeTab = 'preset'">
        <text class="tab-text">预设题组</text>
      </view>
      <view class="tab-item" :class="{ active: activeTab === 'resume' }" role="tab" :aria-selected="activeTab === 'resume'" @click="activeTab = 'resume'">
        <text class="tab-text">简历匹配</text>
      </view>
    </view>

    <!-- Preset Mode -->
    <template v-if="activeTab === 'preset'">
      <view class="section-card">
        <text class="section-title">面试预设</text>
        <view class="preset-list">
          <view
            v-for="preset in presets"
            :key="preset.key"
            class="preset-card"
            :class="{ active: selectedPresetKey === preset.key }"
            @click="selectPreset(preset.key)"
          >
            <view class="preset-head">
              <text class="preset-index">{{ String((presets.findIndex((item) => item.key === preset.key) || 0) + 1).padStart(2, '0') }}</text>
              <text class="preset-title">{{ preset.title }}</text>
            </view>
            <text class="preset-summary">{{ preset.summary }}</text>
            <view class="chip-row">
              <text v-for="tag in preset.tags" :key="tag" class="chip">{{ tag }}</text>
              <text class="chip strong">{{ preset.questionCount }} 题</text>
            </view>
          </view>
        </view>
      </view>
    </template>

    <!-- Resume Mode -->
    <template v-if="activeTab === 'resume'">
      <view class="section-card">
        <text class="section-title">上传简历</text>
        <text class="section-desc">上传 PDF 简历，AI 将分析技术栈并匹配面试题</text>

        <view v-if="!resumeProfile" class="upload-area" role="button" aria-label="点击选择 PDF 简历" @click="pickResume">
          <text class="upload-icon" aria-hidden="true">+</text>
          <text class="upload-hint">点击选择 PDF 简历</text>
          <text class="upload-limit">支持 PDF 格式，最大 10MB</text>
        </view>

        <view v-else class="uploaded-file">
          <view class="file-info">
            <text class="file-name">{{ resumeUploadMeta?.fileName || 'resume.pdf' }}</text>
            <text class="file-size">{{ formatFileSize(resumeUploadMeta?.size || 0) }}</text>
          </view>
          <view class="resume-status">
            <text class="resume-status-chip" :class="resumeParseStatusClass">{{ resumeParseStatusLabel }}</text>
            <text v-if="resumeProfile.resumeSummary" class="resume-status-copy">{{ resumeProfile.resumeSummary }}</text>
            <text v-else-if="resumeProfile.parseError" class="resume-status-error">{{ resumeProfile.parseError }}</text>
          </view>
          <text class="file-remove" role="button" aria-label="移除简历" @click="removeResume">移除</text>
        </view>

        <!-- Question count selector -->
        <view v-if="resumeProfile" class="question-count-row">
          <text class="count-label">面试题数量</text>
          <view class="count-pills">
            <view
              v-for="n in [3, 5, 8, 10]"
              :key="n"
              class="count-pill"
              :class="{ active: resumeQuestionCount === n }"
              @click="resumeQuestionCount = n"
            >
              <text class="pill-text">{{ n }} 题</text>
            </view>
          </view>
        </view>

        <!-- Preset fallback selector -->
        <view v-if="resumeProfile" class="fallback-row">
          <text class="count-label">补充题组（可选）</text>
          <view class="preset-list compact">
            <view
              v-for="preset in presets"
              :key="preset.key"
              class="preset-card mini"
              :class="{ active: resumePresetKey === preset.key }"
              @click="resumePresetKey = resumePresetKey === preset.key ? '' : preset.key"
            >
              <text class="preset-title">{{ preset.title }}</text>
            </view>
          </view>
        </view>

        <!-- Preview button -->
        <button
          v-if="resumeProfile"
          class="preview-btn"
          :disabled="previewLoading || !canPreviewResume"
          @click="previewResume"
        >
          {{ previewLoading ? '分析中...' : '预览面试计划' }}
        </button>

        <!-- Resume Preview Result -->
        <view v-if="resumePlan" class="preview-result">
          <view class="preview-header">
            <text class="preview-title">面试计划预览</text>
            <view class="preview-stats">
              <text class="stat-chip">{{ resumePlan.extractedKeywords.length }} 个关键词</text>
              <text class="stat-chip">{{ resumePlan.questions.length }} 道题</text>
              <text v-if="resumePlan.usedPresetFallback" class="stat-chip warn">含补充题</text>
            </view>
          </view>
          <text class="preview-summary">{{ resumePlan.resumeSummary }}</text>
          <view class="keyword-row">
            <text v-for="kw in resumePlan.extractedKeywords" :key="kw" class="keyword-chip">{{ kw }}</text>
          </view>
          <view class="question-preview-list">
            <view v-for="(q, idx) in resumePlan.questions" :key="idx" class="question-preview-item">
              <view class="q-head">
                <text class="q-index">{{ idx + 1 }}</text>
                <text class="q-title">{{ q.title }}</text>
                <text class="q-source" :class="q.sourceType.toLowerCase()">{{ sourceLabel(q.sourceType) }}</text>
              </view>
              <text class="q-prompt">{{ q.prompt }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>

    <view class="section-card">
      <text class="section-title">面试时长</text>
      <text class="section-desc">默认 60 分钟，支持延长到 90 或 120 分钟，时长越长可追问次数越多。</text>
      <view class="question-count-row">
        <view class="count-pills">
          <view
            v-for="minutes in durationOptions"
            :key="minutes"
            class="count-pill"
            :class="{ active: durationMinutes === minutes }"
            @click="durationMinutes = minutes"
          >
            <text class="pill-text">{{ minutes }} 分钟</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Answer Mode (shared) -->
    <view class="section-card">
      <text class="section-title">回答模式偏好</text>
      <view class="mode-row">
        <view
          class="mode-card"
          :class="{ active: answerModePreference === 'VOICE' }"
          @click="answerModePreference = 'VOICE'"
        >
          <text class="mode-title">语音优先</text>
          <text class="mode-copy">按住说话，适合模拟真实面试节奏。</text>
        </view>
        <view
          class="mode-card"
          :class="{ active: answerModePreference === 'TEXT' }"
          @click="answerModePreference = 'TEXT'"
        >
          <text class="mode-title">文字优先</text>
          <text class="mode-copy">先用文本整理答案，再决定是否录音。</text>
        </view>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" :disabled="!canStart" @click="goToSession">进入面试</button>
      <button class="secondary-btn" @click="goHome">返回首页</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { listInterviewPresets, previewResumePlan } from '@/services/interviewApi'
import { uploadResumeByBrowserFile, uploadResumeByFilePath } from '@/services/mediaApi'
import { useUserStore } from '@/stores/user'
import type { InterviewPreset, ResumeInterviewPlan, ResumeProfilePayload } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const presets = ref<InterviewPreset[]>([])
const selectedPresetKey = ref('backend-core')
const answerModePreference = ref<'VOICE' | 'TEXT'>('VOICE')
const userStore = useUserStore()
const activeTab = ref<'preset' | 'resume'>('preset')

const resumeProfile = ref<ResumeProfilePayload | null>(null)
const resumeUploadMeta = ref<{ fileName: string; size: number } | null>(null)
const resumeQuestionCount = ref(5)
const resumePresetKey = ref('')
const resumePlan = ref<ResumeInterviewPlan | null>(null)
const previewLoading = ref(false)
const durationMinutes = ref(60)
const durationOptions = [60, 90, 120]

const canStart = computed(() => {
  if (activeTab.value === 'preset') return true
  return resumeProfile.value?.parseStatus === 'PARSED'
})

const canPreviewResume = computed(() => resumeProfile.value?.parseStatus === 'PARSED')

const resumeParseStatusLabel = computed(() => {
  if (!resumeProfile.value) {
    return '未上传'
  }
  const statusMap: Record<ResumeProfilePayload['parseStatus'], string> = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    PARSED: '已解析',
    FAILED: '解析失败',
  }
  return statusMap[resumeProfile.value.parseStatus]
})

const resumeParseStatusClass = computed(() => resumeProfile.value?.parseStatus.toLowerCase() || 'uploaded')

onLoad(async (query) => {
  const redirect = typeof query?.durationMinutes === 'string' || typeof query?.presetKey === 'string'
    ? `/pages/interview/setup?durationMinutes=${query?.durationMinutes || ''}&presetKey=${query?.presetKey || ''}`
    : '/pages/interview/setup'
  if (!ensureAuthenticated(userStore, redirect)) {
    return
  }

  if (query?.answerMode === 'TEXT' || query?.answerMode === 'VOICE') {
    answerModePreference.value = query.answerMode
  }
  if (typeof query?.durationMinutes === 'string') {
    durationMinutes.value = parseInt(query.durationMinutes, 10) || 60
  }
  if (typeof query?.presetKey === 'string' && query.presetKey) {
    selectedPresetKey.value = decodeURIComponent(query.presetKey)
    activeTab.value = 'preset'
  }

  try {
    const payload = await listInterviewPresets(API_BASE_URL)
    if (!payload.success) {
      throw new Error(payload.message || '获取面试预设失败')
    }
    presets.value = payload.data
    if (!presets.value.some((item) => item.key === selectedPresetKey.value) && presets.value.length > 0) {
      selectedPresetKey.value = presets.value[0].key
    }
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '获取面试预设失败',
      icon: 'none',
      duration: 2400,
    })
  }
})

const selectPreset = (presetKey: string) => {
  selectedPresetKey.value = presetKey
}

const pickResume = () => {
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,application/pdf'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (file.size > 10 * 1024 * 1024) {
      uni.showToast({ title: '文件不能超过 10MB', icon: 'none' })
      return
    }
    try {
      uni.showLoading({ title: '上传中...' })
      const result = await uploadResumeByBrowserFile(API_BASE_URL, file)
      if (!result.success) throw new Error(result.message || '上传失败')
      resumeProfile.value = result.data
      resumeUploadMeta.value = {
        fileName: file.name,
        size: file.size,
      }
      resumePlan.value = null
      if (result.data.parseStatus === 'FAILED' && result.data.parseError) {
        uni.showToast({ title: result.data.parseError, icon: 'none', duration: 2600 })
      }
    } catch (e) {
      uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
    } finally {
      uni.hideLoading()
    }
  }
  input.click()
  // #endif

  // #ifndef H5
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['pdf'],
    success: async (res) => {
      const file = res.tempFiles[0]
      if (file.size > 10 * 1024 * 1024) {
        uni.showToast({ title: '文件不能超过 10MB', icon: 'none' })
        return
      }
      try {
        uni.showLoading({ title: '上传中...' })
        const result = await uploadResumeByFilePath(API_BASE_URL, file.path, file.name)
        if (!result.success) throw new Error(result.message || '上传失败')
        resumeProfile.value = result.data
        resumeUploadMeta.value = {
          fileName: file.name,
          size: file.size,
        }
        resumePlan.value = null
        if (result.data.parseStatus === 'FAILED' && result.data.parseError) {
          uni.showToast({ title: result.data.parseError, icon: 'none', duration: 2600 })
        }
      } catch (e) {
        uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
  })
  // #endif
}

const removeResume = () => {
  resumeProfile.value = null
  resumeUploadMeta.value = null
  resumePlan.value = null
}

const previewResume = async () => {
  if (!resumeProfile.value) return
  previewLoading.value = true
  try {
    const result = await previewResumePlan(API_BASE_URL, {
      resumeFileId: resumeProfile.value.mediaFileId,
      presetKey: resumePresetKey.value || undefined,
      questionCount: resumeQuestionCount.value,
    })
    if (!result.success) throw new Error(result.message || '分析失败')
    resumePlan.value = result.data
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '简历分析失败', icon: 'none' })
  } finally {
    previewLoading.value = false
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const sourceLabel = (type: string) => {
  const map: Record<string, string> = { LIBRARY: '题库', AI_RESUME: 'AI生成', PRESET: '预设' }
  return map[type] || type
}

const goToSession = () => {
  if (activeTab.value === 'resume' && resumeProfile.value) {
    const params = new URLSearchParams()
    params.set('resumeFileId', resumeProfile.value.mediaFileId)
    params.set('questionCount', String(resumeQuestionCount.value))
    params.set('durationMinutes', String(durationMinutes.value))
    params.set('answerMode', answerModePreference.value)
    params.set('presetTitle', '简历面试')
    if (resumePresetKey.value) params.set('presetKey', resumePresetKey.value)
    uni.navigateTo({
      url: `/pages/interview/session?${params.toString()}`,
    })
  } else {
    const preset = presets.value.find((item) => item.key === selectedPresetKey.value)
    const title = preset?.title || '模拟面试'
    uni.navigateTo({
      url: `/pages/interview/session?presetKey=${encodeURIComponent(selectedPresetKey.value)}&presetTitle=${encodeURIComponent(title)}&answerMode=${answerModePreference.value}&durationMinutes=${durationMinutes.value}`,
    })
  }
}

const goHome = () => {
  uni.reLaunch({
    url: '/pages/index/index',
  })
}
</script>

<style>
/* setup-screen 继承 studio-screen 布局 */
.setup-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

/* 卡片：hero-panel / section-card / action-panel 共用 studio-card 样式 */
.hero-panel, .section-card, .action-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* --- Tab --- */
.tab-bar { display: flex; gap: 0; margin-bottom: 8rpx; border-bottom: 1rpx solid rgba(255, 255, 255, 0.06); }
.tab-item { flex: 1; padding: 18rpx 0; text-align: center; font-size: 26rpx; color: var(--studio-text-muted); border-bottom: 2rpx solid transparent; transition: color 120ms, border-color 120ms; }
.tab-item.active { color: var(--studio-signal); border-bottom-color: var(--studio-signal); font-weight: 600; }

/* --- Preset --- */
.preset-list { display: flex; flex-direction: column; gap: 14rpx; }
.preset-list.compact { gap: 10rpx; }
.preset-card { padding: 22rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); transition: border-color 120ms; }
.preset-card.active { border-color: var(--studio-signal); background: rgba(129, 140, 248, 0.06); }
.preset-card.mini { padding: 14rpx 18rpx; }
.preset-head { display: flex; align-items: center; gap: 14rpx; }
.preset-index { width: 48rpx; height: 48rpx; border-radius: 8rpx; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; color: var(--studio-signal); background: rgba(129, 140, 248, 0.12); }
.preset-title { font-size: 28rpx; font-weight: 600; color: var(--studio-text); }
.preset-summary { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.6; color: var(--studio-text-muted); }

/* chip-row / chip 已在 studio.css 全局定义 */
.chip.strong { font-weight: 600; }

/* --- Section --- */
.section-desc { display: block; font-size: 24rpx; line-height: 1.6; color: var(--studio-text-muted); }

/* --- Upload --- */
.upload-area { margin-top: 16rpx; padding: 40rpx 20rpx; border-radius: var(--studio-radius); border: 1rpx dashed rgba(255, 255, 255, 0.12); text-align: center; }
.upload-icon { font-size: 48rpx; color: var(--studio-text-soft); }
.upload-hint { display: block; margin-top: 12rpx; font-size: 24rpx; color: var(--studio-text-muted); }
.upload-limit { display: block; margin-top: 6rpx; font-size: 20rpx; color: var(--studio-text-soft); }

.uploaded-file { margin-top: 16rpx; }
.file-info { padding: 18rpx; border-radius: var(--studio-radius); background: rgba(129, 140, 248, 0.06); border: 1rpx solid rgba(129, 140, 248, 0.15); display: flex; justify-content: space-between; align-items: center; }
.file-name { font-size: 24rpx; color: var(--studio-text); }
.file-size { font-size: 20rpx; color: var(--studio-text-soft); }
.resume-status { display: flex; flex-direction: column; gap: 8rpx; margin-top: 12rpx; }
.resume-status-chip { display: inline-flex; align-self: flex-start; padding: 6rpx 14rpx; border-radius: 999rpx; font-size: 20rpx; }
.resume-status-chip.uploaded { background: rgba(129, 140, 248, 0.12); color: var(--studio-signal-soft); }
.resume-status-chip.parsing { background: rgba(251, 191, 36, 0.14); color: #fbbf24; }
.resume-status-chip.parsed { background: rgba(74, 222, 128, 0.12); color: var(--studio-success); }
.resume-status-chip.failed { background: rgba(255, 125, 115, 0.14); color: var(--studio-danger); }
.resume-status-copy { font-size: 22rpx; line-height: 1.6; color: var(--studio-text-muted); }
.resume-status-error { font-size: 22rpx; line-height: 1.6; color: var(--studio-danger); }
.file-remove { font-size: 22rpx; color: var(--studio-danger); }

/* --- Question Count --- */
.question-count-row { margin-top: 16rpx; }
.count-label { display: block; font-size: 24rpx; color: var(--studio-text-muted); margin-bottom: 12rpx; }
.count-pills { display: flex; gap: 12rpx; }
.count-pill { flex: 1; padding: 16rpx 0; text-align: center; border-radius: var(--studio-radius); font-size: 26rpx; color: var(--studio-text-muted); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.08); }
.count-pill.active { color: var(--studio-signal); background: rgba(129, 140, 248, 0.1); border-color: var(--studio-signal); }
.pill-text { font-size: 26rpx; }

/* --- Fallback --- */
.fallback-row { margin-top: 16rpx; }

/* --- Preview --- */
.preview-btn { margin-top: 16rpx; border: none; border-radius: var(--studio-radius); font-size: 26rpx; padding: 18rpx 0; width: 100%; background: rgba(129, 140, 248, 0.12); color: var(--studio-signal); font-weight: 600; }
.preview-btn:disabled { opacity: 0.5; }

.preview-result { margin-top: 20rpx; }
.preview-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14rpx; }
.preview-title { display: block; font-size: 28rpx; font-weight: 600; color: var(--studio-text); }
.preview-stats { display: flex; gap: 8rpx; }
.stat-chip { padding: 4rpx 12rpx; border-radius: 6rpx; font-size: 20rpx; background: rgba(129, 140, 248, 0.08); color: var(--studio-signal-soft); }
.stat-chip.warn { background: rgba(251, 191, 36, 0.12); color: #fbbf24; }
.preview-summary { display: block; font-size: 24rpx; line-height: 1.6; color: var(--studio-text-muted); margin-bottom: 12rpx; }

.keyword-row { display: flex; flex-wrap: wrap; gap: 8rpx; margin-bottom: 16rpx; }
.keyword-chip { padding: 6rpx 14rpx; border-radius: 8rpx; font-size: 22rpx; background: rgba(129, 140, 248, 0.08); color: var(--studio-signal-soft); border: 1rpx solid rgba(129, 140, 248, 0.12); }

.question-preview-list { display: flex; flex-direction: column; gap: 10rpx; }
.question-preview-item { padding: 18rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); }
.q-head { display: flex; align-items: center; gap: 10rpx; }
.q-index { width: 36rpx; height: 36rpx; border-radius: 6rpx; display: flex; align-items: center; justify-content: center; font-size: 20rpx; font-weight: 700; color: var(--studio-signal); background: rgba(129, 140, 248, 0.12); }
.q-title { flex: 1; font-size: 26rpx; font-weight: 600; color: var(--studio-text); }
.q-source { display: inline-block; padding: 4rpx 12rpx; border-radius: 6rpx; font-size: 20rpx; }
.q-source.library { background: rgba(74, 222, 128, 0.12); color: var(--studio-success); }
.q-source.ai_resume { background: rgba(129, 140, 248, 0.12); color: var(--studio-signal); }
.q-source.preset { background: rgba(251, 191, 36, 0.12); color: #fbbf24; }
.q-prompt { display: block; margin-top: 8rpx; font-size: 24rpx; line-height: 1.6; color: var(--studio-text-muted); }

/* --- Answer Mode --- */
.mode-row { display: flex; gap: 12rpx; }
.mode-card { flex: 1; padding: 20rpx 16rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); text-align: center; transition: border-color 120ms; }
.mode-card.active { border-color: var(--studio-signal); background: rgba(129, 140, 248, 0.06); }
.mode-title { display: block; font-size: 26rpx; font-weight: 600; color: var(--studio-text); }
.mode-copy { display: block; margin-top: 8rpx; font-size: 22rpx; line-height: 1.5; color: var(--studio-text-muted); }

/* --- ghost-btn (setup 特有) --- */
.ghost-btn { border: none; border-radius: var(--studio-radius); font-size: 28rpx; padding: 22rpx 0; width: 100%; background: transparent; color: var(--studio-text-soft); margin-top: 8rpx; }
</style>
