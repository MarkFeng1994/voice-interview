<template>
  <div class="login-page">
    <div class="login-ambient"></div>
    <div class="login-container fade-in">
      <div class="login-brand">
        <div class="brand-icon">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="8" fill="#6366f1"/>
            <path d="M10 20V12L16 8L22 12V20L16 24L10 20Z" stroke="white" stroke-width="2" fill="none"/>
            <circle cx="16" cy="16" r="3" fill="white"/>
          </svg>
        </div>
        <h1>Voice Interview</h1>
        <p>AI 面试管理控制台</p>
      </div>

      <a-form layout="vertical" @submit.prevent="handleLogin">
        <a-form-item label="用户名">
          <a-input v-model:value="form.username" placeholder="输入用户名" size="large">
            <template #prefix>
              <UserOutlined style="color: var(--text-muted)" />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password v-model:value="form.password" placeholder="输入密码" size="large">
            <template #prefix>
              <LockOutlined style="color: var(--text-muted)" />
            </template>
          </a-input-password>
        </a-form-item>
        <a-button
          type="primary"
          html-type="submit"
          :loading="loading"
          block
          size="large"
          class="login-btn"
        >
          登录
        </a-button>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    message.success('登录成功')
    router.push('/')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-deep);
  position: relative;
  overflow: hidden;
}

.login-ambient {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.08) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.login-container {
  width: 400px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 40px;
  position: relative;
  z-index: 1;
  box-shadow: var(--shadow-md);
}

.login-brand {
  text-align: center;
  margin-bottom: 36px;
}

.brand-icon {
  display: inline-flex;
  margin-bottom: 16px;
}

.login-brand h1 {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.5px;
  margin-bottom: 4px;
}

.login-brand p {
  color: var(--text-secondary);
  font-size: 14px;
}

.login-btn {
  margin-top: 8px;
  height: 44px;
  font-weight: 600;
  font-size: 15px;
  background: var(--accent) !important;
  border: none !important;
  border-radius: var(--radius) !important;
}

.login-btn:hover {
  background: var(--accent-hover) !important;
}
</style>
