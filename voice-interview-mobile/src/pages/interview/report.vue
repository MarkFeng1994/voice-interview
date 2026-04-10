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
      <text class="score-copy">状态：{{ report?.status || '暂无' }}</text>
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
      <view v-for="item in report?.questionReports || []" :key="item.questionIndex" class="bullet-card">
        <text class="bullet-title">第 {{ item.questionIndex }} 题 · {{ item.title }}</text>
        <text class="bullet-copy">得分：{{ item.score ?? '--' }}</text>
        <text class="bullet-copy">{{ item.summary }}</text>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="goToSession">继续练习</button>
      <button class="secondary-btn" @click="goToHistory">查看历史</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { getInterviewReport } from '@/services/interviewApi'
import { useUserStore } from '@/stores/user'
import type { InterviewReport } from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const report = ref<InterviewReport | null>(null)
const missingSession = ref(false)
const userStore = useUserStore()

onLoad(async (query) => {
  if (!ensureAuthenticated(userStore, typeof query?.sessionId === 'string' ? `/pages/interview/report?sessionId=${query.sessionId}` : '/pages/interview/report')) {
    return
  }
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
  uni.navigateTo({
    url: '/pages/interview/setup',
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

.bullet-card { padding: 18rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); margin-bottom: 10rpx; }
.bullet-title { display: block; font-size: 24rpx; font-weight: 600; color: var(--studio-text); margin-bottom: 6rpx; }
.bullet-copy { display: block; font-size: 25rpx; line-height: 1.6; color: var(--studio-text-muted); }

.action-panel.compact { margin-top: 16rpx; }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */
</style>
