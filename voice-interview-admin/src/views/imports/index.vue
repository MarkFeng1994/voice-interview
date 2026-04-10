<template>
  <div>
    <div class="page-header">
      <h1>导入管理</h1>
      <p>通过文本粘贴批量导入面试题目</p>
    </div>

    <a-row :gutter="[20, 20]">
      <a-col :xs="24" :md="10">
        <div class="section-card">
          <h3><ImportOutlined /> 文本导入</h3>
          <a-form layout="vertical" @finish="handleImport">
            <a-form-item label="导入到分类">
              <a-select v-model:value="form.categoryId" placeholder="选择目标分类">
                <a-select-option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="文件标记名">
              <a-input v-model:value="form.fileName" placeholder="manual-import.txt" />
            </a-form-item>
            <a-form-item label="导入文本">
              <a-textarea
                v-model:value="form.rawText"
                :rows="10"
                placeholder="每道题一个块。第一行是标题，后续行是题干，空行或 --- 分隔不同题目。"
              />
            </a-form-item>
            <a-button type="primary" html-type="submit" :loading="importing" block>
              执行导入
            </a-button>
          </a-form>
        </div>
      </a-col>

      <a-col :xs="24" :md="14">
        <div class="section-card">
          <h3><HistoryOutlined /> 导入历史</h3>
          <a-spin :spinning="loading">
            <a-empty v-if="!tasks.length" description="暂无导入任务" />
            <div v-else class="task-list">
              <div v-for="t in tasks" :key="t.id" class="task-item">
                <div class="task-info">
                  <div class="task-title">
                    任务 #{{ t.id }}
                    <a-tag :color="statusColor(t.status)" size="small">{{ t.status }}</a-tag>
                  </div>
                  <div class="task-meta">
                    {{ categoryName(t.categoryId) }}
                    &middot; 成功 {{ t.successCount }}/{{ t.totalCount }}
                    <template v-if="t.fileName"> &middot; {{ t.fileName }}</template>
                  </div>
                  <div v-if="t.errorMessage" class="task-error">{{ t.errorMessage }}</div>
                </div>
              </div>
            </div>
          </a-spin>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ImportOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import * as api from '@/services/libraryApi'
import type { CategoryItem, ImportTaskItem } from '@/types'

const loading = ref(false)
const importing = ref(false)
const categories = ref<CategoryItem[]>([])
const tasks = ref<ImportTaskItem[]>([])

const form = reactive({ categoryId: '', fileName: 'manual-import.txt', rawText: '' })

function categoryName(id: string) {
  return categories.value.find((c) => c.id === id)?.name || id
}

function statusColor(s: string) {
  const map: Record<string, string> = { COMPLETED: 'green', FAILED: 'red', PENDING: 'blue', PROCESSING: 'orange' }
  return map[s] || 'default'
}

async function load() {
  loading.value = true
  try {
    const [cats, ts] = await Promise.all([api.getCategories(), api.getImportTasks()])
    categories.value = cats
    tasks.value = ts
    if (!form.categoryId && cats[0]) form.categoryId = cats[0].id
  } finally {
    loading.value = false
  }
}

async function handleImport() {
  if (!form.categoryId || !form.rawText.trim()) {
    message.warning('请选择分类并输入导入文本')
    return
  }
  importing.value = true
  try {
    const result = await api.importText({
      categoryId: form.categoryId,
      rawText: form.rawText,
      fileName: form.fileName.trim() || 'manual-import.txt',
    })
    message.success(`导入完成：${result.successCount}/${result.totalCount}`)
    form.rawText = ''
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    importing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.task-item {
  padding: 14px 16px;
  border-radius: 8px;
  transition: background var(--transition);
}

.task-item:hover {
  background: var(--bg-hover);
}

.task-title {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.task-error {
  font-size: 12px;
  color: var(--red);
  margin-top: 4px;
}
</style>
