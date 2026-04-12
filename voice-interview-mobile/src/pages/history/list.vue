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

    <view class="filter-panel">
      <view class="filter-head">
        <text class="section-title">筛选状态</text>
        <text class="filter-summary">{{ filterSummary }}</text>
      </view>
      <view class="filter-row">
        <view
          v-for="option in filterOptions"
          :key="option.value"
          class="filter-pill"
          :class="{ active: activeFilter === option.value }"
          @click="activeFilter = option.value"
        >
          <text class="filter-pill-label">{{ option.label }}</text>
          <text class="filter-pill-count">{{ option.count }}</text>
        </view>
      </view>
    </view>

    <view v-if="filteredHistoryItems.length" class="history-list">
      <view v-for="item in filteredHistoryItems" :key="item.sessionId" class="history-card">
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
    <view v-else class="empty-state">
      <text class="empty-title">{{ emptyStateTitle }}</text>
      <text class="empty-text">{{ emptyStateCopy }}</text>
      <view class="empty-actions">
        <button class="secondary-btn" @click="resetFilter">查看全部</button>
        <button class="primary-btn" @click="() => goToSession()">再来一轮练习</button>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="() => goToSession()">再来一轮练习</button>
      <button class="secondary-btn" @click="goHome">返回首页</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { listInterviewSessions } from '@/services/interviewApi'
import { useUserStore } from '@/stores/user'
import type { InterviewSessionSummary } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const historyItems = ref<InterviewSessionSummary[]>([])
const activeFilter = ref<'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'>('ALL')
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

const countByStatus = (status: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED') =>
  historyItems.value.filter((item) => item.status === status).length

const filterOptions = computed(() => [
  { value: 'ALL' as const, label: '全部', count: historyItems.value.length },
  { value: 'IN_PROGRESS' as const, label: '进行中', count: countByStatus('IN_PROGRESS') },
  { value: 'COMPLETED' as const, label: '已完成', count: countByStatus('COMPLETED') },
  { value: 'CANCELLED' as const, label: '已结束', count: countByStatus('CANCELLED') },
])

const filteredHistoryItems = computed(() => {
  if (activeFilter.value === 'ALL') {
    return historyItems.value
  }
  return historyItems.value.filter((item) => item.status === activeFilter.value)
})

const filterSummary = computed(() => {
  const current = filterOptions.value.find((option) => option.value === activeFilter.value)
  return `${current?.label || '全部'} · ${filteredHistoryItems.value.length} 条`
})

const emptyStateTitle = computed(() => {
  switch (activeFilter.value) {
    case 'IN_PROGRESS':
      return '没有进行中的会话'
    case 'COMPLETED':
      return '还没有已完成的报告'
    case 'CANCELLED':
      return '还没有已结束的会话'
    default:
      return '还没有历史会话'
  }
})

const emptyStateCopy = computed(() => {
  switch (activeFilter.value) {
    case 'IN_PROGRESS':
      return '开始一轮新的模拟面试后，这里会显示可继续恢复的进行中会话。'
    case 'COMPLETED':
      return '完整完成一轮面试后，这里会保留可回看的历史报告。'
    case 'CANCELLED':
      return '手动结束的会话会集中展示在这里，方便做中途复盘。'
    default:
      return '先开始一轮练习，后续这里会累积你的历史记录。'
  }
})

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

const resetFilter = () => {
  activeFilter.value = 'ALL'
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

.hero-panel, .action-panel, .filter-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* hero-top / eyebrow / hero-chip / title / subtitle 已在 studio.css 全局定义 */

.filter-head { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; margin-bottom: 14rpx; }
.filter-summary { font-size: 22rpx; color: var(--studio-text-soft); }
.filter-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.filter-pill {
  min-width: 156rpx;
  padding: 14rpx 16rpx;
  border-radius: var(--studio-radius);
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}
.filter-pill.active {
  background: rgba(129, 140, 248, 0.08);
  border-color: rgba(129, 140, 248, 0.2);
}
.filter-pill-label { display: block; font-size: 24rpx; color: var(--studio-text); }
.filter-pill-count { display: block; margin-top: 6rpx; font-size: 22rpx; color: var(--studio-text-soft); }

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

.empty-state {
  padding: 54rpx 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  text-align: center;
}
.empty-title { display: block; font-size: 30rpx; font-weight: 600; color: var(--studio-text); }
.empty-text { display: block; margin-top: 10rpx; font-size: 26rpx; line-height: 1.6; color: var(--studio-text-soft); }
.empty-actions { display: flex; gap: 12rpx; margin-top: 20rpx; }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */
</style>
