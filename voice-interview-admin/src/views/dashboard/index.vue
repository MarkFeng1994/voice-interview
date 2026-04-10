<template>
  <div>
    <div class="page-header fade-in">
      <h1>仪表盘</h1>
      <p>系统概览与关键指标</p>
    </div>

    <a-row :gutter="[20, 20]" style="margin-bottom: 28px">
      <a-col :xs="12" :sm="12" :md="6" v-for="(card, i) in statCards" :key="card.label">
        <div :class="['stat-card', 'fade-in', `fade-in-delay-${i + 1}`]">
          <div class="stat-icon" :style="{ background: card.iconBg, color: card.iconColor }">
            <component :is="card.icon" />
          </div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[20, 20]">
      <a-col :xs="24" :md="12">
        <div class="section-card fade-in fade-in-delay-3">
          <h3><AudioOutlined /> 最近面试</h3>
          <a-empty v-if="!sessions.length" description="暂无面试记录" />
          <div v-else class="recent-list">
            <div v-for="s in sessions.slice(0, 5)" :key="s.sessionId" class="recent-item" @click="router.push('/interviews')">
              <div class="recent-info">
                <div class="recent-title">{{ s.title }}</div>
                <div class="recent-meta">{{ s.answeredRounds }}/{{ s.totalQuestions }} 题 &middot; {{ s.status }}</div>
              </div>
              <div v-if="s.overallScore != null" class="recent-score" :style="{ color: scoreColor(s.overallScore) }">
                {{ s.overallScore }}
              </div>
              <div v-else class="recent-score" style="color: var(--text-muted)">--</div>
            </div>
          </div>
        </div>
      </a-col>

      <a-col :xs="24" :md="12">
        <div class="section-card fade-in fade-in-delay-4">
          <h3><MonitorOutlined /> Provider 状态</h3>
          <a-empty v-if="!metrics.length" description="暂无指标数据" />
          <div v-else class="recent-list">
            <div v-for="m in metrics" :key="`${m.capability}-${m.provider}`" class="recent-item">
              <div class="recent-info">
                <div class="recent-title">{{ m.capability }}</div>
                <div class="recent-meta">{{ m.provider }} &middot; {{ m.totalCalls }} 次调用 &middot; {{ m.averageLatencyMs }}ms</div>
              </div>
              <div class="recent-score" :style="{ color: m.failureCalls > 0 ? 'var(--red)' : 'var(--green)' }">
                {{ m.totalCalls > 0 ? Math.round((m.successCalls / m.totalCalls) * 100) : 0 }}%
              </div>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  FileTextOutlined,
  AudioOutlined,
  TrophyOutlined,
  MonitorOutlined,
} from '@ant-design/icons-vue'
import * as libraryApi from '@/services/libraryApi'
import * as interviewApi from '@/services/interviewApi'
import * as systemApi from '@/services/systemApi'
import type { CategoryItem, QuestionItem, InterviewSessionSummary, ProviderMetricItem } from '@/types'

const router = useRouter()
const categories = ref<CategoryItem[]>([])
const questions = ref<QuestionItem[]>([])
const sessions = ref<InterviewSessionSummary[]>([])
const metrics = ref<ProviderMetricItem[]>([])

const avgScore = computed(() => {
  const scored = sessions.value.filter((s) => s.overallScore != null)
  if (!scored.length) return '--'
  return Math.round(scored.reduce((sum, s) => sum + (s.overallScore ?? 0), 0) / scored.length)
})

const statCards = computed(() => [
  { label: '题目分类', value: categories.value.length, icon: h(AppstoreOutlined), iconBg: 'var(--accent-dim)', iconColor: 'var(--accent)' },
  { label: '题库总量', value: questions.value.length, icon: h(FileTextOutlined), iconBg: 'var(--cyan-dim)', iconColor: 'var(--cyan)' },
  { label: '面试场次', value: sessions.value.length, icon: h(AudioOutlined), iconBg: 'var(--amber-dim)', iconColor: 'var(--amber)' },
  { label: '平均得分', value: avgScore.value, icon: h(TrophyOutlined), iconBg: 'var(--green-dim)', iconColor: 'var(--green)' },
])

function scoreColor(score: number) {
  if (score >= 80) return 'var(--green)'
  if (score >= 60) return 'var(--amber)'
  return 'var(--red)'
}

onMounted(async () => {
  const results = await Promise.allSettled([
    libraryApi.getCategories(),
    libraryApi.getQuestions(),
    interviewApi.getSessions(),
    systemApi.getMetrics(),
  ])
  if (results[0].status === 'fulfilled') categories.value = results[0].value
  if (results[1].status === 'fulfilled') questions.value = results[1].value
  if (results[2].status === 'fulfilled') sessions.value = results[2].value
  if (results[3].status === 'fulfilled') metrics.value = results[3].value
})
</script>

<style scoped>
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: background var(--transition);
}

.recent-item:hover {
  background: var(--bg-hover);
}

.recent-info {
  flex: 1;
  min-width: 0;
}

.recent-title {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.recent-score {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -1px;
  margin-left: 16px;
  flex-shrink: 0;
}
</style>
