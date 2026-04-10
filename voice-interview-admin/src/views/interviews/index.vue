<template>
  <div>
    <div class="page-header">
      <h1>面试记录</h1>
      <p>查看历史面试会话与 AI 评估报告</p>
    </div>

    <a-row :gutter="[20, 20]">
      <a-col :xs="24" :md="8">
        <div class="section-card" style="min-height: 500px">
          <h3><UnorderedListOutlined /> 会话列表</h3>
          <a-spin :spinning="loading">
            <a-empty v-if="!sessions.length" description="暂无面试记录" />
            <div v-else class="session-list">
              <div
                v-for="s in sessions"
                :key="s.sessionId"
                :class="['session-item', { active: selectedId === s.sessionId }]"
                @click="selectSession(s.sessionId)"
              >
                <div class="session-title">{{ s.title }}</div>
                <div class="session-meta">
                  <a-tag :color="statusColor(s.status)" size="small">{{ s.status }}</a-tag>
                  {{ s.answeredRounds }}/{{ s.totalQuestions }} 题
                </div>
                <div v-if="s.overallScore != null" class="session-score" :style="{ color: scoreColor(s.overallScore) }">
                  {{ s.overallScore }}分
                </div>
              </div>
            </div>
          </a-spin>
        </div>
      </a-col>

      <a-col :xs="24" :md="16">
        <div class="section-card" style="min-height: 500px">
          <template v-if="report">
            <div class="report-header">
              <div>
                <h2 style="margin-bottom: 4px">{{ report.title }}</h2>
                <a-tag :color="statusColor(report.status)">{{ report.status }}</a-tag>
              </div>
              <div class="score-ring">
                <svg width="100" height="100" viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="42" fill="none" stroke="var(--border)" stroke-width="8" />
                  <circle
                    cx="50" cy="50" r="42"
                    fill="none"
                    :stroke="scoreColor(report.overallScore ?? 0)"
                    stroke-width="8"
                    stroke-linecap="round"
                    :stroke-dasharray="`${((report.overallScore ?? 0) / 100) * 264} 264`"
                  />
                </svg>
                <div class="score-value" :style="{ color: scoreColor(report.overallScore ?? 0) }">
                  {{ report.overallScore ?? '--' }}
                </div>
              </div>
            </div>

            <p style="color: var(--text-secondary); margin: 16px 0">{{ report.overallComment }}</p>

            <a-row :gutter="[16, 16]" style="margin-bottom: 20px">
              <a-col :span="8">
                <div class="insight-box green">
                  <h4>优势</h4>
                  <ul>
                    <li v-for="s in report.strengths" :key="s">{{ s }}</li>
                  </ul>
                  <p v-if="!report.strengths.length" class="empty-hint">暂无</p>
                </div>
              </a-col>
              <a-col :span="8">
                <div class="insight-box amber">
                  <h4>待加强</h4>
                  <ul>
                    <li v-for="w in report.weaknesses" :key="w">{{ w }}</li>
                  </ul>
                  <p v-if="!report.weaknesses.length" class="empty-hint">暂无</p>
                </div>
              </a-col>
              <a-col :span="8">
                <div class="insight-box accent">
                  <h4>建议</h4>
                  <ul>
                    <li v-for="s in report.suggestions" :key="s">{{ s }}</li>
                  </ul>
                  <p v-if="!report.suggestions.length" class="empty-hint">暂无</p>
                </div>
              </a-col>
            </a-row>

            <h3 style="margin-bottom: 12px">各题详情</h3>
            <a-collapse>
              <a-collapse-panel
                v-for="q in report.questionReports"
                :key="q.questionIndex"
                :header="`第 ${q.questionIndex + 1} 题：${q.title}`"
              >
                <template #extra>
                  <span v-if="q.score != null" :style="{ fontWeight: 700, color: scoreColor(q.score) }">
                    {{ q.score }}分
                  </span>
                </template>
                <p><strong>题目：</strong>{{ q.prompt }}</p>
                <p style="margin-top: 8px"><strong>评语：</strong>{{ q.summary }}</p>
              </a-collapse-panel>
            </a-collapse>
          </template>

          <a-empty v-else description="选择左侧面试记录查看报告" style="margin-top: 120px" />
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { UnorderedListOutlined } from '@ant-design/icons-vue'
import * as api from '@/services/interviewApi'
import type { InterviewSessionSummary, InterviewReport } from '@/types'

const loading = ref(false)
const sessions = ref<InterviewSessionSummary[]>([])
const report = ref<InterviewReport | null>(null)
const selectedId = ref('')

function statusColor(s: string) {
  const map: Record<string, string> = { COMPLETED: 'green', IN_PROGRESS: 'blue', CREATED: 'default', CANCELLED: 'red' }
  return map[s] || 'default'
}

function scoreColor(score: number) {
  if (score >= 80) return 'var(--green)'
  if (score >= 60) return 'var(--amber)'
  return 'var(--red)'
}

async function selectSession(id: string) {
  selectedId.value = id
  try {
    report.value = await api.getReport(id)
  } catch {
    report.value = null
  }
}

async function load() {
  loading.value = true
  try {
    sessions.value = await api.getSessions()
    if (sessions.value[0]) await selectSession(sessions.value[0].sessionId)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  padding: 14px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background var(--transition);
  position: relative;
}

.session-item:hover {
  background: var(--bg-hover);
}

.session-item.active {
  background: var(--accent-dim);
  border-left: 3px solid var(--accent);
}

.session-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
}

.session-meta {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.session-score {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
  font-weight: 800;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.insight-box {
  background: var(--bg-elevated);
  border-radius: var(--radius);
  padding: 16px;
  min-height: 120px;
}

.insight-box h4 {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}

.insight-box ul {
  padding-left: 16px;
  font-size: 13px;
  color: var(--text-secondary);
}

.insight-box ul li {
  margin-bottom: 4px;
}

.insight-box.green h4 { color: var(--green); }
.insight-box.amber h4 { color: var(--amber); }
.insight-box.accent h4 { color: var(--accent); }

.empty-hint {
  color: var(--text-muted);
  font-size: 13px;
}
</style>
