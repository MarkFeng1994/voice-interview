<template>
  <div>
    <div class="page-header">
      <div>
        <h1>系统监控</h1>
        <p>AI / ASR / TTS Provider 调用指标与运行状态</p>
      </div>
      <a-button @click="load">
        <ReloadOutlined /> 刷新
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!metrics.length" description="暂无 Provider 指标数据" />

      <a-row :gutter="[20, 20]" v-else>
        <a-col :xs="24" :md="8" v-for="m in metrics" :key="`${m.capability}-${m.provider}`">
          <div class="monitor-card">
            <div class="monitor-header">
              <div class="monitor-cap">{{ m.capability }}</div>
              <a-tag :color="m.failureCalls > 0 ? 'red' : 'green'" size="small">
                {{ m.provider }}
              </a-tag>
            </div>

            <div class="monitor-stats">
              <div class="monitor-stat">
                <div class="monitor-stat-value">{{ m.totalCalls }}</div>
                <div class="monitor-stat-label">总调用</div>
              </div>
              <div class="monitor-stat">
                <div class="monitor-stat-value" style="color: var(--green)">{{ m.successCalls }}</div>
                <div class="monitor-stat-label">成功</div>
              </div>
              <div class="monitor-stat">
                <div class="monitor-stat-value" :style="{ color: m.failureCalls > 0 ? 'var(--red)' : 'var(--text-muted)' }">
                  {{ m.failureCalls }}
                </div>
                <div class="monitor-stat-label">失败</div>
              </div>
            </div>

            <div class="monitor-bar">
              <div class="monitor-bar-label">成功率</div>
              <a-progress
                :percent="m.totalCalls > 0 ? Math.round((m.successCalls / m.totalCalls) * 100) : 0"
                :stroke-color="m.failureCalls > 0 ? '#ef4444' : '#22c55e'"
                size="small"
              />
            </div>

            <div class="monitor-bar">
              <div class="monitor-bar-label">平均延迟</div>
              <div class="monitor-latency">{{ m.averageLatencyMs }}ms</div>
            </div>

            <div class="monitor-bar">
              <div class="monitor-bar-label">最近延迟</div>
              <div class="monitor-latency">{{ m.lastLatencyMs }}ms</div>
            </div>

            <div v-if="m.lastError" class="monitor-error">
              <ExclamationCircleOutlined /> {{ m.lastError }}
            </div>
          </div>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ReloadOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import * as systemApi from '@/services/systemApi'
import type { ProviderMetricItem } from '@/types'

const loading = ref(false)
const metrics = ref<ProviderMetricItem[]>([])

async function load() {
  loading.value = true
  try {
    metrics.value = await systemApi.getMetrics()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.monitor-card {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
  transition: border-color var(--transition), transform var(--transition);
}

.monitor-card:hover {
  border-color: var(--border-light);
  transform: translateY(-2px);
}

.monitor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.monitor-cap {
  font-size: 16px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.monitor-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.monitor-stat-value {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -1px;
}

.monitor-stat-label {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.monitor-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.monitor-bar-label {
  font-size: 13px;
  color: var(--text-secondary);
  flex-shrink: 0;
  margin-right: 16px;
}

.monitor-latency {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.monitor-error {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--red-dim);
  border-radius: 8px;
  font-size: 12px;
  color: var(--red);
  word-break: break-all;
}
</style>
