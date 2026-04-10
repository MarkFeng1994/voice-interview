<template>
  <a-layout class="admin-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="240"
      :collapsed-width="68"
      :trigger="null"
      collapsible
      class="admin-sider"
    >
      <div class="sider-brand" @click="router.push('/')">
        <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#6366f1"/>
          <path d="M10 20V12L16 8L22 12V20L16 24L10 20Z" stroke="white" stroke-width="2" fill="none"/>
          <circle cx="16" cy="16" r="3" fill="white"/>
        </svg>
        <span v-show="!collapsed" class="brand-text">Voice Interview</span>
      </div>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        class="sider-menu"
        @click="handleMenuClick"
      >
        <a-menu-item key="/">
          <template #icon><DashboardOutlined /></template>
          <span>仪表盘</span>
        </a-menu-item>
        <a-menu-item key="/categories">
          <template #icon><AppstoreOutlined /></template>
          <span>分类管理</span>
        </a-menu-item>
        <a-menu-item key="/questions">
          <template #icon><FileTextOutlined /></template>
          <span>题库管理</span>
        </a-menu-item>
        <a-menu-item key="/imports">
          <template #icon><ImportOutlined /></template>
          <span>导入管理</span>
        </a-menu-item>
        <a-menu-item key="/interviews">
          <template #icon><AudioOutlined /></template>
          <span>面试记录</span>
        </a-menu-item>
        <a-menu-item key="/monitor">
          <template #icon><MonitorOutlined /></template>
          <span>系统监控</span>
        </a-menu-item>
      </a-menu>

      <div class="sider-footer">
        <a-button type="text" class="collapse-btn" @click="appStore.toggleSidebar()">
          <MenuUnfoldOutlined v-if="collapsed" />
          <MenuFoldOutlined v-else />
        </a-button>
      </div>
    </a-layout-sider>

    <a-layout class="admin-main">
      <header class="admin-header">
        <div class="header-left">
          <a-breadcrumb>
            <a-breadcrumb-item>
              <router-link to="/">首页</router-link>
            </a-breadcrumb-item>
            <a-breadcrumb-item v-if="currentPageName">{{ currentPageName }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-dropdown>
            <div class="user-pill">
              <a-avatar :size="28" style="background: var(--accent)">
                {{ auth.profile?.nickname?.charAt(0) || 'U' }}
              </a-avatar>
              <span v-if="auth.profile" class="user-name">{{ auth.profile.nickname }}</span>
              <DownOutlined style="font-size: 10px; color: var(--text-muted)" />
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item disabled>
                  <span style="color: var(--text-muted)">{{ auth.profile?.username }}</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item @click="handleLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DashboardOutlined,
  AppstoreOutlined,
  FileTextOutlined,
  ImportOutlined,
  AudioOutlined,
  MonitorOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DownOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const appStore = useAppStore()

const collapsed = computed({
  get: () => appStore.sidebarCollapsed,
  set: (v: boolean) => { appStore.sidebarCollapsed = v },
})

const selectedKeys = computed(() => [route.path])

const pageNames: Record<string, string> = {
  '/': '',
  '/categories': '分类管理',
  '/questions': '题库管理',
  '/imports': '导入管理',
  '/interviews': '面试记录',
  '/monitor': '系统监控',
}

const currentPageName = computed(() => pageNames[route.path] || '')

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

watch(() => auth.isLoggedIn, (v) => {
  if (!v) router.push('/login')
})
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: var(--bg-base);
}

.admin-sider {
  background: var(--bg-deep) !important;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  position: fixed !important;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
}

.admin-sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sider-brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.brand-text {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.3px;
  white-space: nowrap;
}

.sider-menu {
  flex: 1;
  padding: 12px 8px;
  background: transparent !important;
  border-right: none !important;
}

.sider-menu :deep(.ant-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px !important;
  color: var(--text-secondary) !important;
  font-weight: 500;
  font-size: 14px;
}

.sider-menu :deep(.ant-menu-item:hover) {
  color: var(--text-primary) !important;
  background: var(--bg-hover) !important;
}

.sider-menu :deep(.ant-menu-item-selected) {
  background: var(--accent-dim) !important;
  color: var(--accent-hover) !important;
}

.sider-menu :deep(.ant-menu-item-selected::after) {
  display: none;
}

.sider-footer {
  padding: 12px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.collapse-btn {
  width: 100%;
  color: var(--text-muted) !important;
}

.admin-main {
  margin-left: 240px;
  transition: margin-left var(--transition);
  background: var(--bg-base);
}

.admin-sider:deep(.ant-layout-sider-collapsed) ~ .admin-main,
.admin-layout :deep(.ant-layout-sider-collapsed) ~ .admin-main {
  margin-left: 68px;
}

.admin-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: var(--bg-base);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(12px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px 4px 4px;
  border-radius: 24px;
  transition: background var(--transition);
}

.user-pill:hover {
  background: var(--bg-hover);
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.admin-content {
  padding: 28px;
  min-height: calc(100vh - 60px);
}
</style>
