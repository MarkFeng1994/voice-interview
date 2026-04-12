<template>
  <view class="report-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">After Action</text>
        <text class="hero-chip">Report Console</text>
      </view>
      <text class="title">{{ report?.title || '模拟面试报告' }}</text>
      <text class="subtitle">
        {{ report?.overallComment || '这里会展示本轮面试的总体评价、题目表现、薄弱项和下一步建议。' }}
      </text>
    </view>

    <view class="score-card">
      <text class="score-label">总体评分</text>
      <text class="score-value">{{ report?.overallScore ?? '--' }}</text>
      <text class="score-copy">状态：{{ report?.status || '暂无' }} · {{ report?.questionReports.length || 0 }} 题回顾</text>
    </view>

    <view v-if="report && contextBanner" class="section-card context-card">
      <text class="section-title">进入上下文</text>
      <text class="bullet-copy">{{ contextBanner }}</text>
    </view>

    <view v-if="report?.status === 'CANCELLED'" class="section-card cancelled-card">
      <text class="section-title">本轮已手动结束</text>
      <text class="bullet-copy">
        这轮面试是在未完成全部题目的情况下结束的，当前报告更适合作为中途复盘，而不是完整成绩单。
      </text>
      <view class="chip-row">
        <text class="chip warn">可继续复盘当前回答</text>
        <text class="chip warn">建议再完整练一轮</text>
      </view>
    </view>

    <view v-if="report?.overallExplanation" class="section-card explanation-card">
      <view class="section-head">
        <text class="section-title">为什么是这个结论</text>
        <view class="chip-row">
          <text :class="['diagnosis-level', `tone-${getLevelMeta(report.overallExplanation.level).tone}`]">
            {{ getLevelMeta(report.overallExplanation.level).label }}
          </text>
          <text class="explanation-chip">{{ getGeneratedByLabel(report.overallExplanation.generatedBy) }}</text>
        </view>
      </view>
      <text class="bullet-copy">{{ report.overallExplanation.summaryText }}</text>

      <view v-if="report.overallExplanation.evidencePoints.length" class="compact-card">
        <text class="bullet-title">证据点</text>
        <view
          v-for="point in report.overallExplanation.evidencePoints"
          :key="point"
          class="evidence-item"
        >
          <text class="evidence-dot">•</text>
          <text class="bullet-copy">{{ point }}</text>
        </view>
      </view>

      <view v-if="report.overallExplanation.improvementSuggestions.length" class="compact-card">
        <text class="bullet-title">下一步建议</text>
        <view
          v-for="suggestion in report.overallExplanation.improvementSuggestions"
          :key="suggestion"
          class="evidence-item"
        >
          <text class="evidence-dot">•</text>
          <text class="bullet-copy">{{ suggestion }}</text>
        </view>
      </view>
    </view>

    <view v-if="missingSession" class="section-card">
      <text class="section-title">无法加载报告</text>
      <text class="bullet-copy">当前没有可用的 `sessionId`。请先开始一场练习，或从历史页进入报告。</text>
      <view class="action-panel compact">
        <button class="secondary-btn" @click="goToHistory">查看历史</button>
        <button class="primary-btn" @click="goHome">返回首页</button>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">优势领域</text>
      <view class="chip-row">
        <text v-for="item in report?.strengths || []" :key="item" class="chip">{{ item }}</text>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">待加强</text>
      <view v-for="item in report?.weaknesses || []" :key="item" class="bullet-card">
        <text class="bullet-title">薄弱项</text>
        <text class="bullet-copy">{{ item }}</text>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">下一步建议</text>
      <view v-for="item in report?.suggestions || []" :key="item" class="bullet-card">
        <text class="bullet-title">建议</text>
        <text class="bullet-copy">{{ item }}</text>
      </view>
    </view>

    <view class="section-card">
      <text class="section-title">题目明细</text>
      <view v-for="item in report?.questionReports || []" :key="item.questionIndex" class="bullet-card question-card">
        <text class="bullet-title">第 {{ item.questionIndex }} 题 · {{ item.title }}</text>
        <text class="bullet-copy">得分：{{ item.score ?? '--' }}</text>
        <text class="bullet-copy">{{ item.summary }}</text>
        <text class="question-prompt">{{ item.prompt }}</text>

        <view v-if="item.explanation" class="compact-card explanation-card">
          <view class="section-head">
            <text class="bullet-title">诊断解释</text>
            <view class="chip-row">
              <text :class="['diagnosis-level', `tone-${getLevelMeta(item.explanation.performanceLevel).tone}`]">
                {{ getLevelMeta(item.explanation.performanceLevel).label }}
              </text>
              <text class="explanation-chip">{{ getGeneratedByLabel(item.explanation.generatedBy) }}</text>
            </view>
          </view>
          <text class="bullet-copy">{{ item.explanation.summaryText }}</text>

          <view v-if="item.explanation.evidencePoints.length" class="compact-card">
            <text class="bullet-title">证据点</text>
            <view
              v-for="point in item.explanation.evidencePoints"
              :key="`${item.questionIndex}-${point}`"
              class="evidence-item"
            >
              <text class="evidence-dot">•</text>
              <text class="bullet-copy">{{ point }}</text>
            </view>
          </view>

          <view v-if="item.explanation.improvementSuggestion" class="compact-card">
            <text class="bullet-title">改进建议</text>
            <text class="bullet-copy">{{ item.explanation.improvementSuggestion }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="goToSession">继续练习</button>
      <button class="secondary-btn" @click="goToHistory">查看历史</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { getInterviewReport } from '@/services/interviewApi'
import { useUserStore } from '@/stores/user'
import type { ExplanationGeneratedBy, InterviewExplanationLevel, InterviewReport } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const report = ref<InterviewReport | null>(null)
const missingSession = ref(false)
const source = ref<'history' | 'session-end' | 'direct'>('direct')
const preservedPresetKey = ref('')
const preservedPresetTitle = ref('')
const preservedAnswerMode = ref<'VOICE' | 'TEXT'>('VOICE')
const preservedDurationMinutes = ref(60)
const preservedResumeFileId = ref('')
const preservedQuestionCount = ref(0)
const userStore = useUserStore()

type DiagnosisTone = 'strong' | 'medium' | 'weak' | 'neutral'

const getLevelMeta = (level: InterviewExplanationLevel | null | undefined): { label: string; tone: DiagnosisTone } => {
  switch (level) {
    case 'STRONG':
      return {
        label: '表现扎实',
        tone: 'strong',
      }
    case 'MEDIUM':
      return {
        label: '基础可用',
        tone: 'medium',
      }
    case 'WEAK':
      return {
        label: '需要加强',
        tone: 'weak',
      }
    default:
      return {
        label: '数据不足',
        tone: 'neutral',
      }
  }
}

const getGeneratedByLabel = (generatedBy: ExplanationGeneratedBy): string => {
  return generatedBy === 'RULE_PLUS_LLM' ? 'AI 润色' : '规则生成'
}

const contextBanner = computed(() => {
  if (!report.value) {
    return ''
  }
  if (source.value === 'session-end') {
    return report.value.status === 'CANCELLED'
      ? '你刚刚结束了当前面试，这是一份基于已完成内容生成的即时复盘。'
      : '你刚刚完成了一轮面试，这是一份即时生成的报告。'
  }
  if (source.value === 'history') {
    return report.value.status === 'CANCELLED'
      ? '这是你从历史记录打开的一份已结束会话报告。'
      : '这是你从历史记录打开的原始报告，可用于继续复盘。'
  }
  return report.value.status === 'CANCELLED'
    ? '这是当前会话的已结束报告。'
    : '这是当前会话的面试报告。'
})

onLoad(async (query) => {
  if (!ensureAuthenticated(userStore, typeof query?.sessionId === 'string' ? `/pages/interview/report?sessionId=${query.sessionId}` : '/pages/interview/report')) {
    return
  }
  if (query?.source === 'history' || query?.source === 'session-end') {
    source.value = query.source
  }
  preservedPresetKey.value = typeof query?.presetKey === 'string' ? decodeURIComponent(query.presetKey) : ''
  preservedPresetTitle.value = typeof query?.presetTitle === 'string' ? decodeURIComponent(query.presetTitle) : ''
  preservedAnswerMode.value = query?.answerMode === 'TEXT' ? 'TEXT' : 'VOICE'
  preservedDurationMinutes.value = typeof query?.durationMinutes === 'string'
    ? parseInt(query.durationMinutes, 10) || 60
    : 60
  preservedResumeFileId.value = typeof query?.resumeFileId === 'string' ? query.resumeFileId : ''
  preservedQuestionCount.value = typeof query?.questionCount === 'string'
    ? parseInt(query.questionCount, 10) || 0
    : 0
  const sessionId = typeof query?.sessionId === 'string' ? query.sessionId : ''
  if (!sessionId) {
    missingSession.value = true
    uni.showToast({
      title: '缺少 sessionId，先去完成一场面试',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  try {
    const payload = await getInterviewReport(API_BASE_URL, sessionId)
    if (!payload.success) {
      throw new Error(payload.message || '获取报告失败')
    }
    report.value = payload.data
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '获取报告失败',
      icon: 'none',
      duration: 2200,
    })
  }
})

const goToSession = () => {
  const canResumeDirectly = Boolean(
    preservedPresetKey.value || preservedResumeFileId.value || preservedPresetTitle.value,
  )
  if (canResumeDirectly) {
    const params = new URLSearchParams()
    if (preservedPresetKey.value) {
      params.set('presetKey', preservedPresetKey.value)
    }
    if (preservedPresetTitle.value) {
      params.set('presetTitle', preservedPresetTitle.value)
    } else if (report.value?.title) {
      params.set('presetTitle', report.value.title)
    }
    if (preservedResumeFileId.value) {
      params.set('resumeFileId', preservedResumeFileId.value)
    }
    if (preservedQuestionCount.value) {
      params.set('questionCount', String(preservedQuestionCount.value))
    }
    if (preservedDurationMinutes.value) {
      params.set('durationMinutes', String(preservedDurationMinutes.value))
    }
    params.set('answerMode', preservedAnswerMode.value)
    uni.navigateTo({
      url: `/pages/interview/session?${params.toString()}`,
    })
    return
  }

  const setupParams = new URLSearchParams()
  setupParams.set('durationMinutes', String(preservedDurationMinutes.value))
  setupParams.set('answerMode', preservedAnswerMode.value)
  if (preservedPresetKey.value) {
    setupParams.set('presetKey', preservedPresetKey.value)
  }
  if (preservedPresetTitle.value || report.value?.title) {
    setupParams.set('presetTitle', preservedPresetTitle.value || report.value?.title || '模拟面试')
  }
  uni.navigateTo({
    url: `/pages/interview/setup?${setupParams.toString()}`,
  })
}

const goToHistory = () => {
  uni.navigateTo({
    url: '/pages/history/list',
  })
}

const goHome = () => {
  uni.reLaunch({
    url: '/pages/index/index',
  })
}
</script>

<style>
.report-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.hero-panel, .score-card, .section-card, .action-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* hero-top / eyebrow / hero-chip / title / subtitle / section-title / chip-row / chip 已在 studio.css 全局定义 */

.score-label { display: block; font-size: 22rpx; letter-spacing: 1.2rpx; color: var(--studio-text-soft); text-transform: uppercase; }
.score-value { display: block; margin-top: 10rpx; font-family: var(--studio-font-display); font-size: 72rpx; font-weight: 700; line-height: 1; color: var(--studio-text); }
.score-copy { display: block; margin-top: 8rpx; font-size: 24rpx; color: var(--studio-signal); }

.context-card {
  background: linear-gradient(180deg, rgba(129, 140, 248, 0.08), rgba(129, 140, 248, 0.03));
}

.cancelled-card {
  background: linear-gradient(180deg, rgba(248, 113, 113, 0.08), rgba(248, 113, 113, 0.03));
  border-color: rgba(248, 113, 113, 0.16);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 14rpx;
}

.section-head .section-title,
.section-head .bullet-title {
  margin-bottom: 0;
}

.bullet-card { padding: 18rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); margin-bottom: 10rpx; }
.bullet-title { display: block; font-size: 24rpx; font-weight: 600; color: var(--studio-text); margin-bottom: 6rpx; }
.bullet-copy { display: block; font-size: 25rpx; line-height: 1.6; color: var(--studio-text-muted); }
.question-prompt { display: block; margin-top: 10rpx; font-size: 23rpx; line-height: 1.6; color: var(--studio-text-soft); }

.compact-card {
  margin-top: 16rpx;
  padding: 18rpx;
  border-radius: var(--studio-radius);
  background: rgba(255, 255, 255, 0.025);
  border: 1rpx solid rgba(255, 255, 255, 0.05);
}

.explanation-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.035), rgba(255, 255, 255, 0.02));
}

.question-card {
  margin-bottom: 14rpx;
}

.explanation-chip,
.diagnosis-level {
  display: inline-block;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  line-height: 1.4;
  border: 1rpx solid rgba(129, 140, 248, 0.18);
}

.explanation-chip {
  color: var(--studio-signal-soft);
  background: rgba(129, 140, 248, 0.08);
}

.diagnosis-level {
  font-weight: 600;
}

.tone-strong {
  color: #9ae6b4;
  background: rgba(52, 211, 153, 0.12);
  border-color: rgba(52, 211, 153, 0.24);
}

.tone-medium {
  color: #fde68a;
  background: rgba(250, 204, 21, 0.12);
  border-color: rgba(250, 204, 21, 0.24);
}

.tone-weak {
  color: #fca5a5;
  background: rgba(248, 113, 113, 0.12);
  border-color: rgba(248, 113, 113, 0.24);
}

.tone-neutral {
  color: var(--studio-text-soft);
  background: rgba(148, 163, 184, 0.12);
  border-color: rgba(148, 163, 184, 0.2);
}

.chip.warn {
  background: rgba(248, 113, 113, 0.12);
  color: #fecaca;
  border-color: rgba(248, 113, 113, 0.18);
}

.evidence-item {
  display: flex;
  gap: 10rpx;
  align-items: flex-start;
  margin-top: 10rpx;
}

.evidence-dot {
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--studio-signal-soft);
}

.action-panel.compact { margin-top: 16rpx; }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */
</style>
