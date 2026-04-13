<template>
  <view class="login-screen">
    <view class="hero-panel">
      <view class="hero-top">
        <text class="eyebrow">Signal Console</text>
        <text class="hero-chip">Mobile Studio</text>
      </view>
      <text class="title">把回答讲成一场有判断力的对话。</text>
      <text class="subtitle">
        这不是刷题列表，而是一套带追问、语音转写、复盘报告的模拟面试控制台。
      </text>
      <view class="signal-row">
        <text class="signal-pill">实时追问</text>
        <text class="signal-pill">语音转写可手改</text>
        <text class="signal-pill">移动端对话式</text>
      </view>
    </view>

    <view class="form-card">
      <view class="section-head">
        <text class="section-title">{{ isRegisterMode ? '创建账号' : '账号登录' }}</text>
        <text class="section-kicker">{{ isRegisterMode ? '新建候选人档案' : '登录入口' }}</text>
      </view>
      <view class="field-shell">
        <text class="field-label">用户名</text>
        <input
          v-model="form.username"
          class="input-core"
          type="text"
          placeholder="用户名，如 admin"
        />
      </view>
      <view class="field-shell">
        <text class="field-label">密码</text>
        <input
          v-model="form.password"
          class="input-core"
          type="text"
          :password="true"
          placeholder="密码，任意 6 位以上"
        />
      </view>
      <view v-if="isRegisterMode" class="field-shell">
        <text class="field-label">昵称</text>
        <input
          v-model="form.nickname"
          class="input-core"
          type="text"
          placeholder="昵称，如 Coff0xc"
        />
      </view>
      <text class="footnote">登录后会进入练习工作台，后续可以直接从历史会话恢复到上一次的面试进度。</text>
      <button class="primary-btn" @click="submit">
        {{ loading ? '提交中...' : isRegisterMode ? '注册并进入' : '登录并进入' }}
      </button>
      <button class="secondary-btn" @click="toggleMode">
        {{ isRegisterMode ? '已有账号，去登录' : '没有账号，先注册' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isRegisterMode = ref(false)
const loading = ref(false)
const redirectUrl = ref('/pages/index/index')

const form = reactive({
  username: 'admin',
  password: '123456',
  nickname: 'Admin',
})

onLoad((query) => {
  if (typeof query?.redirect === 'string' && query.redirect) {
    redirectUrl.value = decodeURIComponent(query.redirect)
  }
})

const toggleMode = () => {
  isRegisterMode.value = !isRegisterMode.value
}

const submit = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    uni.showToast({
      title: '用户名和密码不能为空',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  if (isRegisterMode.value && !form.nickname.trim()) {
    uni.showToast({
      title: '昵称不能为空',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  loading.value = true
  try {
    if (isRegisterMode.value) {
      await userStore.register({
        username: form.username.trim(),
        password: form.password.trim(),
        nickname: form.nickname.trim(),
      })
    } else {
      await userStore.login({
        username: form.username.trim(),
        password: form.password.trim(),
      })
    }

    uni.showToast({
      title: '登录成功',
      icon: 'success',
      duration: 1200,
    })

    setTimeout(() => {
      uni.reLaunch({
        url: redirectUrl.value,
      })
    }, 300)
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '登录失败',
      icon: 'none',
      duration: 2400,
    })
  } finally {
    loading.value = false
  }
}
</script>

<style>
.login-screen {
  min-height: 100vh;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.hero-panel,
.form-card {
  padding: 28rpx;
  border-radius: var(--studio-radius-lg);
  background: var(--studio-bg-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
}

/* login 的 hero-top 与全局略有不同：用 gap 而非 space-between */
.hero-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

/* eyebrow / hero-chip / title 已在 studio.css 全局定义 */

/* login 的 subtitle margin-top 略大 */
.subtitle {
  margin-top: 14rpx;
}

.signal-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 20rpx;
}

.signal-pill {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: rgba(129, 140, 248, 0.08);
  color: var(--studio-signal-soft);
  border: 1rpx solid rgba(129, 140, 248, 0.12);
}

.section-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.section-title {
  font-family: var(--studio-font-display);
  font-size: 32rpx;
  margin-bottom: 0;
}

.section-kicker {
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  background: rgba(255, 255, 255, 0.04);
  color: var(--studio-text-soft);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.footnote {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 1.65;
  color: var(--studio-text-soft);
}

.field-shell {
  margin-top: 16rpx;
  padding: 16rpx 18rpx 18rpx;
  border-radius: var(--studio-radius);
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.field-label {
  display: block;
  font-size: 22rpx;
  letter-spacing: 1rpx;
  color: var(--studio-text-soft);
  margin-bottom: 8rpx;
}

.input-core {
  width: 100%;
  padding: 8rpx 4rpx;
  background: transparent;
  border: none;
  font-size: 28rpx;
  color: var(--studio-text);
}

/* login 的按钮需要 block + margin-top 覆盖 */
.primary-btn,
.secondary-btn {
  display: block;
  margin-top: 20rpx;
  letter-spacing: 0.5rpx;
}
</style>
