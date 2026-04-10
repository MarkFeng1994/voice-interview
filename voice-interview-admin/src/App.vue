<template>
  <a-config-provider :theme="antTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { theme } from 'ant-design-vue'

const antTheme = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#6366f1',
    colorBgContainer: '#141420',
    colorBgElevated: '#1c1c2e',
    colorBgLayout: '#0c0c14',
    colorBorder: '#2a2a3e',
    colorText: '#e2e2ef',
    colorTextSecondary: '#8b8ba3',
    borderRadius: 10,
    fontFamily: '"Plus Jakarta Sans", "Noto Sans SC", -apple-system, sans-serif',
  },
}
</script>
