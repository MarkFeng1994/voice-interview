<template>
  <view class="profile-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">Voice Studio</text>
        <text class="hero-chip">Profile Deck</text>
      </view>
      <text class="title">{{ userStore.profile?.nickname || '未登录用户' }}</text>
      <text class="subtitle">
        当前版本提供用户资料和面试官语音设置。语音录入后会自动转写到发送框，你可以手动修改再发送。
      </text>
    </view>

    <view class="profile-card">
      <text class="section-title">当前账号</text>
      <view class="profile-row">
        <text class="label">用户名</text>
        <text class="value">{{ userStore.profile?.username || '--' }}</text>
      </view>
      <view class="profile-row">
        <text class="label">昵称</text>
        <text class="value">{{ userStore.profile?.nickname || '--' }}</text>
      </view>
      <view class="profile-row">
        <text class="label">登录状态</text>
        <text class="value">{{ userStore.isLoggedIn ? '已登录' : '未登录' }}</text>
      </view>
      <input v-model="form.nickname" class="input" placeholder="修改昵称" />
      <button class="secondary-btn" @click="saveProfile">保存昵称</button>
    </view>

    <view class="profile-card">
      <text class="section-title">面试官语音</text>
      <text class="label">音色</text>
      <view class="voice-grid">
        <view
          v-for="option in voiceOptions"
          :key="option.id"
          :class="['voice-chip', { active: Number(voiceForm.interviewerSpeakerId) === option.id }]"
          @click="selectVoice(option.id)"
        >
          <text class="voice-chip-name">{{ option.name }}</text>
          <text class="voice-chip-note">{{ option.note }}</text>
        </view>
      </view>
      <view class="profile-row">
        <text class="label">语速</text>
        <input v-model="voiceForm.interviewerSpeechSpeed" class="inline-input" type="digit" />
      </view>
      <text class="helper-copy">当前走阿里云 Qwen realtime 语音。直接点选音色即可，语速会作为表达节奏提示。</text>
      <textarea
        v-model="previewText"
        class="input"
        maxlength="120"
        auto-height
        placeholder="输入一段试听文案，比如：你好，我是今天的 AI 面试官。"
      />
      <view class="voice-actions">
        <button class="secondary-btn" @click="saveVoiceSettings">保存语音设置</button>
        <button class="secondary-btn" @click="previewVoice">{{ previewLoading ? '试听中...' : '试听当前设置' }}</button>
      </view>
    </view>

    <view class="profile-card">
      <text class="section-title">快捷入口</text>
      <view class="quick-grid">
        <button class="mini-btn" @click="goToSession">继续练习</button>
        <button class="mini-btn" @click="goToHistory">历史记录</button>
        <button class="mini-btn" @click="goHome">首页</button>
      </view>
    </view>

    <view class="action-panel">
      <button class="primary-btn" @click="refreshProfile">刷新用户信息</button>
      <button class="secondary-btn danger" @click="logout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL, toAbsoluteUrl } from '@/config/api'
import { useAudioPlayback } from '@/composables/useAudioPlayback'
import { previewTtsVoice } from '@/services/systemApi'
import { useUserStore } from '@/stores/user'
import { ensureAuthenticated, redirectToLogin } from '@/utils/auth'
import { readInterviewVoiceSettings, saveInterviewVoiceSettings } from '@/utils/interviewSettings'

const userStore = useUserStore()
const form = reactive({
  nickname: '',
})
const voiceForm = reactive(readInterviewVoiceSettings())
const previewText = ref('你好，我是今天的 AI 面试官，我们开始一轮模拟面试。')
const previewLoading = ref(false)

const voiceOptions = [
  { id: 33, name: 'Cherry', note: '默认女声，清晰自然' },
  { id: 34, name: 'Serena', note: '偏温和，适合陪练' },
  { id: 35, name: 'Ethan', note: '偏稳重，适合正式面试' },
  { id: 36, name: 'Chelsie', note: '偏轻快，适合日常对话' },
]

const { playAudio } = useAudioPlayback({})

onLoad(async () => {
  if (!ensureAuthenticated(userStore, '/pages/profile/index')) {
    return
  }

  try {
    await userStore.refreshProfile()
    form.nickname = userStore.profile?.nickname || ''
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '刷新用户信息失败',
      icon: 'none',
      duration: 2200,
    })
  }
})

const refreshProfile = async () => {
  try {
    await userStore.refreshProfile()
    form.nickname = userStore.profile?.nickname || ''
    uni.showToast({
      title: '用户信息已刷新',
      icon: 'success',
      duration: 1200,
    })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '刷新用户信息失败',
      icon: 'none',
      duration: 2200,
    })
  }
}

const saveProfile = async () => {
  if (!form.nickname.trim()) {
    uni.showToast({
      title: '昵称不能为空',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  try {
    await userStore.saveProfile({
      nickname: form.nickname.trim(),
    })
    uni.showToast({
      title: '昵称已更新',
      icon: 'success',
      duration: 1200,
    })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '更新用户信息失败',
      icon: 'none',
      duration: 2200,
    })
  }
}

const saveVoiceSettings = () => {
  if (!voiceForm.interviewerSpeakerId) {
    uni.showToast({
      title: '请选择音色',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  if (!voiceForm.interviewerSpeechSpeed || voiceForm.interviewerSpeechSpeed <= 0) {
    uni.showToast({
      title: '语速必须大于 0',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  saveInterviewVoiceSettings({
    interviewerSpeakerId: Number(voiceForm.interviewerSpeakerId),
    interviewerSpeechSpeed: Number(voiceForm.interviewerSpeechSpeed),
  })
  uni.showToast({
    title: '面试官语音设置已保存',
    icon: 'success',
    duration: 1400,
  })
}

const selectVoice = (voiceId: number) => {
  voiceForm.interviewerSpeakerId = voiceId
}

const previewVoice = async () => {
  if (previewLoading.value) {
    return
  }
  if (!previewText.value.trim()) {
    uni.showToast({
      title: '试听文案不能为空',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  previewLoading.value = true
  try {
    const payload = await previewTtsVoice(API_BASE_URL, {
      text: previewText.value.trim(),
      interviewerSpeakerId: Number(voiceForm.interviewerSpeakerId),
      interviewerSpeechSpeed: Number(voiceForm.interviewerSpeechSpeed),
    })
    if (!payload.success) {
      throw new Error(payload.message || '试听失败')
    }
    playAudio(toAbsoluteUrl(payload.data.audioUrl))
    uni.showToast({
      title: '开始试听',
      icon: 'success',
      duration: 1200,
    })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '试听失败',
      icon: 'none',
      duration: 2200,
    })
  } finally {
    previewLoading.value = false
  }
}

const logout = () => {
  userStore.logout()
  redirectToLogin('/pages/index/index')
}

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
.profile-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.hero-panel, .profile-card, .action-panel {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* hero-top / eyebrow / hero-chip / title / subtitle / section-title 已在 studio.css 全局定义 */

.profile-row { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; border-bottom: 1rpx solid rgba(255, 255, 255, 0.04); }
.profile-row:last-child { border-bottom: none; }
.label { font-size: 24rpx; color: var(--studio-text-soft); }
.value { font-size: 24rpx; color: var(--studio-text); }

.input { margin-top: 12rpx; width: 100%; padding: 14rpx 16rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.08); font-size: 26rpx; color: var(--studio-text); }
.inline-input { width: 120rpx; padding: 12rpx 14rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.08); font-size: 26rpx; color: var(--studio-text); text-align: center; }

.helper-copy { display: block; margin-top: 12rpx; font-size: 22rpx; line-height: 1.65; color: var(--studio-text-soft); }

.voice-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12rpx; }
.voice-chip { padding: 18rpx 16rpx; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.03); border: 1rpx solid rgba(255, 255, 255, 0.06); text-align: center; transition: border-color 120ms; }
.voice-chip.active { border-color: var(--studio-signal); background: rgba(129, 140, 248, 0.08); }
.voice-chip-name { display: block; font-size: 26rpx; font-weight: 600; color: var(--studio-text); }
.voice-chip-note { display: block; margin-top: 6rpx; font-size: 20rpx; color: var(--studio-text-soft); }

.voice-actions { margin-top: 16rpx; display: flex; gap: 12rpx; }
.voice-actions button { flex: 1; border: none; border-radius: var(--studio-radius); font-size: 24rpx; padding: 16rpx 0; }
.voice-actions .secondary-btn { margin-top: 0; }

.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; margin-top: 16rpx; }
.mini-btn { border: none; border-radius: var(--studio-radius); background: rgba(255, 255, 255, 0.04); color: var(--studio-text); font-size: 24rpx; border: 1rpx solid rgba(255, 255, 255, 0.08); padding: 18rpx 0; }

/* primary-btn / secondary-btn 已在 studio.css 全局定义 */
.secondary-btn.danger { background: rgba(248, 113, 113, 0.1); color: var(--studio-danger); border: 1rpx solid rgba(248, 113, 113, 0.2); }
</style>
