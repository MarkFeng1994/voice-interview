<template>
  <view class="home-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">Signal Deck</text>
        <text class="hero-chip">Realtime Mock</text>
      </view>
      <text class="title">{{ userStore.profile?.nickname || '候选人' }} 的面试控制台</text>
      <text class="subtitle">
        一套为手机端重做的对话式练习界面。你可以直接开始练习、恢复上次会话、检查语音链路，或进入报告做复盘。
      </text>
      <view class="tag-row">
        <text class="tag">对话式</text>
        <text class="tag">DashScope 语音</text>
        <text class="tag">追问驱动</text>
      </view>
      <view class="hero-dashboard">
        <view class="score-orb">
          <text class="score-caption">最近评分</text>
          <text class="score-number">{{ latestReport?.overallScore ?? '--' }}</text>
          <text class="score-band">{{ scoreBand }}</text>
        </view>
        <view class="diagnostic-column">
          <view class="diagnostic-card">
            <text class="diagnostic-label">最近会话</text>
            <text class="diagnostic-value">{{ latestSession ? latestSession.status : 'NO SESSION' }}</text>
            <text class="diagnostic-copy">{{ latestSession ? `${latestSession.answeredRounds} 轮已答` : '先开始一轮练习' }}</text>
          </view>
          <view class="diagnostic-card">
            <text class="diagnostic-label">当前链路</text>
            <text class="diagnostic-value">{{ providerShortline }}</text>
            <text class="diagnostic-copy">{{ providerLoading ? '同步运行态中...' : '首页展示的是当前真实运行 provider' }}</text>
          </view>
        </view>
      </view>
      <view class="account-row">
        <text class="account-copy">当前账号：{{ userStore.profile?.nickname || userStore.profile?.username || '未登录' }}</text>
        <button class="logout-btn" @click="logout">退出</button>
      </view>
    </view>

    <view class="card-grid">
      <view class="info-card">
        <text class="card-title">服务状态</text>
        <text class="card-copy">
          {{ providerLoading ? '正在检测当前运行态...' : providerSummary ? providerSummaryText : '暂时无法获取服务状态。' }}
        </text>
        <view v-if="providerSummary" class="tag-row service-row">
          <text class="tag" :class="providerSummary.ai.status.toLowerCase()">AI · {{ providerSummary.ai.provider }} · {{ providerSummary.ai.status }}</text>
          <text class="tag" :class="providerSummary.asr.status.toLowerCase()">ASR · {{ providerSummary.asr.provider }} · {{ providerSummary.asr.status }}</text>
          <text class="tag" :class="providerSummary.tts.status.toLowerCase()">TTS · {{ providerSummary.tts.provider }} · {{ providerSummary.tts.status }}</text>
        </view>
      </view>

      <view class="info-card">
        <text class="card-title">开始练习</text>
        <text class="card-copy">直接进入一轮面试，体验当前的语音上传、追问和报告链路。</text>
        <button class="inline-btn" @click="goToSession">立即开始</button>
      </view>
      <view class="info-card">
        <text class="card-title">最近进展</text>
        <text class="card-copy">
          {{ dashboardLoading ? '正在加载最近一次练习...' : latestSession ? `${latestSession.title} · ${latestSession.status} · 已答 ${latestSession.answeredRounds} 轮` : '还没有练习记录，建议先开始一轮面试。' }}
        </text>
        <button
          v-if="latestSession?.status === 'IN_PROGRESS'"
          class="inline-btn"
          @click="resumeLatestSession"
        >
          继续上次练习
        </button>
        <button v-if="latestSession" class="inline-btn secondary" @click="goToHistory">查看历史</button>
      </view>
      <view class="info-card">
        <text class="card-title">今日建议</text>
        <text class="card-copy">
          {{ latestReport?.overallComment || '先做 1 轮高并发专题，再复盘报告里的追问点和表达深度。' }}
        </text>
        <button v-if="latestSession" class="inline-btn secondary" @click="goToLatestReport">查看最近报告</button>
      </view>
      <view class="info-card nav-card">
        <text class="card-title">快速导航</text>
        <view class="nav-grid">
          <button class="mini-btn" @click="goToSession">会话页</button>
          <button class="mini-btn" @click="goToHistory">历史页</button>
          <button class="mini-btn" @click="goToReport">报告页</button>
          <button class="mini-btn" @click="goToProfile">我的</button>
        </view>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="goToSession">开始一轮练习</button>
      <button class="secondary-btn" @click="goToHistory">查看历史记录</button>
      <button class="secondary-btn" @click="goToProfile">查看个人中心</button>
      <button class="secondary-btn" @click="showRoadmap">查看后续规划</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'

import { API_BASE_URL } from '@/config/api'
import { getInterviewReport, listInterviewSessions } from '@/services/interviewApi'
import { fetchProviderRuntime } from '@/services/systemApi'
import { useUserStore } from '@/stores/user'
import type { InterviewReport, InterviewSessionSummary, ProviderRuntimeSummary } from '@/types/interview'
import { ensureAuthenticated, redirectToLogin } from '@/utils/auth'

const userStore = useUserStore()
const latestSession = ref<InterviewSessionSummary | null>(null)
const latestReport = ref<InterviewReport | null>(null)
const dashboardLoading = ref(false)
const providerLoading = ref(false)
const providerSummary = ref<ProviderRuntimeSummary | null>(null)

const providerSummaryText = computed(() => {
  if (!providerSummary.value) {
    return '暂时无法获取服务状态。'
  }
  const statuses = [providerSummary.value.ai, providerSummary.value.asr, providerSummary.value.tts]
    .map((item) => `${item.provider}/${item.status}`)
    .join(' · ')
  return `当前运行链路：${statuses}`
})

const providerShortline = computed(() => {
  if (!providerSummary.value) {
    return 'UNKNOWN'
  }
  return [providerSummary.value.ai.provider, providerSummary.value.asr.provider, providerSummary.value.tts.provider]
    .map((item) => item.toUpperCase())
    .join(' / ')
})

const scoreBand = computed(() => {
  const score = latestReport.value?.overallScore
  if (score == null) {
    return 'WAITING'
  }
  if (score >= 80) {
    return 'STRONG'
  }
  if (score >= 60) {
    return 'STABLE'
  }
  return 'REBUILD'
})

onLoad(async () => {
  if (!ensureAuthenticated(userStore, '/pages/index/index')) {
    return
  }

  dashboardLoading.value = true
  providerLoading.value = true
  try {
    const [listPayload, providerPayload] = await Promise.all([
      listInterviewSessions(API_BASE_URL),
      fetchProviderRuntime(API_BASE_URL),
    ])
    if (listPayload.success && listPayload.data.length > 0) {
      latestSession.value = listPayload.data[0]
      const reportPayload = await getInterviewReport(API_BASE_URL, latestSession.value.sessionId)
      if (reportPayload.success) {
        latestReport.value = reportPayload.data
      }
    }
    if (providerPayload.success) {
      providerSummary.value = providerPayload.data
    }
  } catch {
    // keep the dashboard usable even when latest session fetch fails
  } finally {
    dashboardLoading.value = false
    providerLoading.value = false
  }
})

const goToSession = () => {
  uni.navigateTo({
    url: '/pages/interview/setup',
  })
}

const goToHistory = () => {
  uni.navigateTo({
    url: '/pages/history/list',
  })
}

const goToReport = () => {
  if (!latestSession.value) {
    uni.showToast({
      title: '先完成一轮练习再查看报告',
      icon: 'none',
      duration: 2200,
    })
    return
  }
  uni.navigateTo({
    url: `/pages/interview/report?sessionId=${latestSession.value.sessionId}`,
  })
}

const goToLatestReport = () => {
  if (!latestSession.value) {
    return
  }
  uni.navigateTo({
    url: `/pages/interview/report?sessionId=${latestSession.value.sessionId}`,
  })
}

const resumeLatestSession = () => {
  if (!latestSession.value) {
    return
  }
  uni.navigateTo({
    url: `/pages/interview/session?sessionId=${latestSession.value.sessionId}`,
  })
}

const goToProfile = () => {
  uni.navigateTo({
    url: '/pages/profile/index',
  })
}

const showRoadmap = () => {
  uni.showToast({
    title: '下一步继续补题库、正式报告和语音能力',
    icon: 'none',
    duration: 2200,
  })
}

const logout = () => {
  userStore.logout()
  redirectToLogin('/pages/index/index')
}
</script>

<style>
.home-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.hero-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* hero-top / eyebrow / hero-chip / title / subtitle / tag-row / tag 已在 studio.css 全局定义 */
/* index 的 tag-row 需要额外 margin-top */
.tag-row { margin-top: 16rpx; }

.service-row { margin-top: 12rpx; }

.tag.up { background: rgba(74, 222, 128, 0.12); color: var(--studio-success); border-color: rgba(74, 222, 128, 0.2); }
.tag.configured { background: rgba(96, 165, 250, 0.12); color: var(--studio-info); border-color: rgba(96, 165, 250, 0.2); }
.tag.down { background: rgba(248, 113, 113, 0.12); color: var(--studio-danger); border-color: rgba(248, 113, 113, 0.2); }
.tag.unknown { background: rgba(255, 255, 255, 0.04); color: var(--studio-text-soft); }

.hero-dashboard {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns: 200rpx minmax(0, 1fr);
  gap: 16rpx;
}

.score-orb,
.diagnostic-card {
  padding: 20rpx;
  border-radius: var(--studio-radius);
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

.score-orb { display: flex; flex-direction: column; justify-content: center; min-height: 200rpx; }
.score-caption, .diagnostic-label { font-size: 20rpx; letter-spacing: 1.2rpx; color: var(--studio-text-soft); text-transform: uppercase; }
.score-number { margin-top: 10rpx; font-family: var(--studio-font-display); font-size: 72rpx; line-height: 1; font-weight: 700; color: var(--studio-text); }
.score-band { margin-top: 8rpx; color: var(--studio-signal); font-size: 22rpx; }
.diagnostic-column { display: flex; flex-direction: column; gap: 16rpx; }
.diagnostic-value { display: block; margin-top: 8rpx; font-family: var(--studio-font-display); font-size: 30rpx; font-weight: 600; color: var(--studio-text); }
.diagnostic-copy { display: block; margin-top: 8rpx; font-size: 22rpx; line-height: 1.5; color: var(--studio-text-muted); }

.account-row { margin-top: 16rpx; display: flex; justify-content: space-between; gap: 16rpx; align-items: center; }
.account-copy { font-size: 24rpx; color: var(--studio-text-muted); }
.logout-btn { border: none; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.04); color: var(--studio-text-muted); font-size: 22rpx; padding: 0 20rpx; border: 1rpx solid rgba(255, 255, 255, 0.08); }

.card-grid { display: flex; flex-direction: column; gap: 16rpx; }
.info-card { padding: 24rpx; border-radius: var(--studio-radius-lg); background: var(--studio-bg-soft); border: 1rpx solid rgba(255, 255, 255, 0.06); }
.nav-card { padding-bottom: 24rpx; }
.card-title { display: block; font-family: var(--studio-font-display); font-size: 30rpx; font-weight: 600; color: var(--studio-text); }
.card-copy { display: block; margin-top: 10rpx; font-size: 25rpx; line-height: 1.6; color: var(--studio-text-muted); }

.nav-grid { margin-top: 16rpx; display: flex; flex-wrap: wrap; gap: 12rpx; }
.inline-btn { margin-top: 14rpx; border: none; border-radius: var(--studio-radius); background: var(--studio-signal-strong); color: #fff; font-size: 24rpx; font-weight: 600; }
.inline-btn.secondary { background: rgba(255, 255, 255, 0.04); color: var(--studio-text-muted); border: 1rpx solid rgba(255, 255, 255, 0.08); }
.mini-btn { flex: 1 1 calc(50% - 8rpx); min-width: 160rpx; border: none; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.04); color: var(--studio-text); font-size: 24rpx; border: 1rpx solid rgba(255, 255, 255, 0.08); }

.action-panel { margin-top: auto; display: flex; flex-direction: column; gap: 12rpx; }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */

@media (max-width: 720rpx) {
  .hero-dashboard { grid-template-columns: 1fr; }
}
</style>
