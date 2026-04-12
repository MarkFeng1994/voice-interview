<template>
  <view class="history-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">Session Archive</text>
        <text class="hero-chip">Timeline</text>
      </view>
      <text class="title">历史会话与练习轨迹</text>
      <text class="subtitle">
        这里保留你最近的对话、分数和状态。继续会话时会回到原来的 session，不会重新开一轮。
      </text>
    </view>

    <view class="history-list">
      <view v-for="item in historyItems" :key="item.sessionId" class="history-card">
        <view class="card-head">
          <view>
            <text class="card-title">{{ item.title }}</text>
            <text class="card-meta">{{ item.startedAt || '尚未开始' }}</text>
          </view>
          <text class="score-pill" :class="statusClass(item.status)">{{ scoreLabel(item) }}</text>
        </view>
        <text class="card-copy">{{ summaryLabel(item) }}</text>
        <view class="card-tags">
          <text class="tag">已答 {{ item.answeredRounds }} 轮</text>
          <text class="tag" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</text>
          <text v-if="item.lastUpdatedAt" class="tag subtle">最近更新 {{ item.lastUpdatedAt }}</text>
        </view>
        <view class="card-actions">
          <button class="mini-btn" @click="() => handlePrimaryAction(item)">{{ primaryActionLabel(item.status) }}</button>
          <button class="mini-btn secondary" @click="() => handleSecondaryAction(item)">
            {{ secondaryActionLabel(item.status) }}
          </button>
        </view>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="() => goToSession()">再来一轮练习</button>
      <button class="secondary-btn" @click="goHome">返回首页</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { listInterviewSessions } from '@/services/interviewApi'
import { useUserStore } from '@/stores/user'
import type { InterviewSessionSummary } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const historyItems = ref<InterviewSessionSummary[]>([])
const userStore = useUserStore()

onLoad(async () => {
  if (!ensureAuthenticated(userStore, '/pages/history/list')) {
    return
  }
  try {
    const payload = await listInterviewSessions(API_BASE_URL)
    if (!payload.success) {
      throw new Error(payload.message || '获取历史列表失败')
    }
    historyItems.value = payload.data
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '获取历史列表失败',
      icon: 'none',
      duration: 2200,
    })
  }
})

const statusLabel = (status: string) => {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELLED':
      return '已结束'
    default:
      return status
  }
}

const statusClass = (status: string) => status.toLowerCase()

const scoreLabel = (item: InterviewSessionSummary) => {
  if (item.status === 'CANCELLED') {
    return '已结束'
  }
  if (item.status === 'IN_PROGRESS') {
    return item.overallScore == null ? '进行中' : `${item.overallScore} 分`
  }
  return item.overallScore == null ? '-- 分' : `${item.overallScore} 分`
}

const summaryLabel = (item: InterviewSessionSummary) => {
  if (item.status === 'CANCELLED') {
    return item.summary || '这轮面试已手动结束，可以查看报告后决定是否重新开始。'
  }
  if (item.status === 'IN_PROGRESS') {
    return item.summary || '当前仍可继续作答，系统会从上一次的题目和进度恢复。'
  }
  return item.summary || '本轮报告已生成，可直接查看原报告或再来一轮。'
}

const secondaryActionLabel = (status: string) => {
  switch (status) {
    case 'IN_PROGRESS':
      return '查看报告'
    case 'COMPLETED':
    case 'CANCELLED':
      return '再来一轮'
    default:
      return '再来一轮'
  }
}

const primaryActionLabel = (status: string) => {
  switch (status) {
    case 'IN_PROGRESS':
      return '继续会话'
    case 'COMPLETED':
    case 'CANCELLED':
      return '查看报告'
    default:
      return '查看报告'
  }
}

const goToSession = (sessionId?: string) => {
  uni.navigateTo({
    url: sessionId
      ? `/pages/interview/session?sessionId=${sessionId}`
      : '/pages/interview/setup',
  })
}

const goToReportWithContext = (item: InterviewSessionSummary) => {
  const params = new URLSearchParams()
  params.set('sessionId', item.sessionId)
  params.set('source', 'history')
  if (item.durationMinutes) {
    params.set('durationMinutes', String(item.durationMinutes))
  }
  if (item.title) {
    params.set('presetTitle', item.title)
  }
  if (item.stage) {
    params.set('stage', item.stage)
  }
  uni.navigateTo({
    url: `/pages/interview/report?${params.toString()}`,
  })
}

const handlePrimaryAction = (item: InterviewSessionSummary) => {
  if (item.status === 'IN_PROGRESS') {
    goToSession(item.sessionId)
    return
  }
  goToReportWithContext(item)
}

const handleSecondaryAction = (item: InterviewSessionSummary) => {
  if (item.status === 'IN_PROGRESS') {
    goToReportWithContext(item)
    return
  }
  goToSession()
}

const goHome = () => {
  uni.reLaunch({
    url: '/pages/index/index',
  })
}
</script>

<style>
.history-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.hero-panel, .action-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* hero-top / eyebrow / hero-chip / title / subtitle 已在 studio.css 全局定义 */

.history-list { display: flex; flex-direction: column; gap: 14rpx; }

.history-card {
  padding: 22rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

.card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12rpx; }
.card-title { font-size: 28rpx; font-weight: 600; color: var(--studio-text); flex: 1; min-width: 0; }
.card-meta { font-size: 22rpx; color: var(--studio-text-soft); margin-top: 4rpx; }
.score-pill { padding: 6rpx 14rpx; border-radius: 8rpx; font-size: 22rpx; font-weight: 600; background: rgba(129, 140, 248, 0.1); color: var(--studio-signal); flex-shrink: 0; }
.score-pill.in_progress { background: rgba(129, 140, 248, 0.12); color: var(--studio-signal); }
.score-pill.completed { background: rgba(74, 222, 128, 0.12); color: var(--studio-success); }
.score-pill.cancelled { background: rgba(248, 113, 113, 0.12); color: var(--studio-danger); }
.card-copy { display: block; margin-top: 10rpx; font-size: 24rpx; line-height: 1.6; color: var(--studio-text-muted); }

.card-tags { display: flex; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; }
.card-tags .tag { padding: 6rpx 14rpx; border-radius: 8rpx; font-size: 20rpx; background: rgba(255, 255, 255, 0.04); color: var(--studio-text-soft); border: 1rpx solid rgba(255, 255, 255, 0.06); }
.card-tags .tag.in_progress { background: rgba(129, 140, 248, 0.1); color: var(--studio-signal); border-color: rgba(129, 140, 248, 0.2); }
.card-tags .tag.completed { background: rgba(74, 222, 128, 0.1); color: var(--studio-success); border-color: rgba(74, 222, 128, 0.2); }
.card-tags .tag.cancelled { background: rgba(248, 113, 113, 0.1); color: var(--studio-danger); border-color: rgba(248, 113, 113, 0.2); }
.card-tags .tag.subtle { background: rgba(255, 255, 255, 0.03); color: var(--studio-text-muted); }

.card-actions { display: flex; gap: 10rpx; margin-top: 14rpx; }
.mini-btn { flex: 1; border: none; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.04); color: var(--studio-text-muted); font-size: 24rpx; border: 1rpx solid rgba(255, 255, 255, 0.08); padding: 14rpx 0; }
.mini-btn.primary { background: var(--studio-signal-strong); color: #fff; font-weight: 600; border: none; }
.mini-btn.secondary { background: rgba(255, 255, 255, 0.06); color: var(--studio-text-muted); }

.empty-state { padding: 60rpx 20rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--studio-text-soft); }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */
</style>
