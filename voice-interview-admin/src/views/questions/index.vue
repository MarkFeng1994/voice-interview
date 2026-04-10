<template>
  <div>
    <div class="page-header">
      <div>
        <h1>题库管理</h1>
        <p>管理面试题目，支持分类筛选与难度标记</p>
      </div>
      <a-button type="primary" @click="showModal = true">
        <PlusOutlined /> 新建题目
      </a-button>
    </div>

    <div class="section-card" style="margin-bottom: 20px">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-select v-model:value="filterCategory" placeholder="按分类筛选" allowClear style="width: 100%">
            <a-select-option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :span="6">
          <a-select v-model:value="filterDifficulty" placeholder="按难度筛选" allowClear style="width: 100%">
            <a-select-option :value="1">简单</a-select-option>
            <a-select-option :value="2">中等</a-select-option>
            <a-select-option :value="3">困难</a-select-option>
          </a-select>
        </a-col>
        <a-col :span="10">
          <a-input-search v-model:value="searchText" placeholder="搜索题目标题..." allowClear />
        </a-col>
      </a-row>
    </div>

    <a-table
      :data-source="filteredQuestions"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="{ pageSize: 15, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 题` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'category'">
          {{ categoryName(record.categoryId) }}
        </template>
        <template v-if="column.key === 'difficulty'">
          <span :class="['difficulty-tag', `difficulty-${record.difficulty}`]">
            {{ difficultyLabel(record.difficulty) }}
          </span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="editQuestion(record)">编辑</a-button>
            <a-popconfirm title="确认删除？" @confirm="removeQuestion(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="showModal"
      :title="editingId ? '编辑题目' : '新建题目'"
      :width="640"
      @ok="saveQuestion"
      @cancel="resetForm"
      ok-text="保存"
      cancel-text="取消"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="所属分类">
              <a-select v-model:value="form.categoryId" placeholder="选择分类">
                <a-select-option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="难度">
              <a-select v-model:value="form.difficulty">
                <a-select-option :value="1">简单</a-select-option>
                <a-select-option :value="2">中等</a-select-option>
                <a-select-option :value="3">困难</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="题目标题">
          <a-input v-model:value="form.title" placeholder="如：请解释 Java 中的多态性" />
        </a-form-item>
        <a-form-item label="题目内容">
          <a-textarea v-model:value="form.content" :rows="4" placeholder="完整的题目描述" />
        </a-form-item>
        <a-form-item label="参考答案">
          <a-textarea v-model:value="form.answer" :rows="3" placeholder="可选，供 AI 评判参考" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import * as api from '@/services/libraryApi'
import type { CategoryItem, QuestionItem } from '@/types'

const loading = ref(false)
const showModal = ref(false)
const editingId = ref('')
const categories = ref<CategoryItem[]>([])
const questions = ref<QuestionItem[]>([])
const filterCategory = ref<string | undefined>(undefined)
const filterDifficulty = ref<number | undefined>(undefined)
const searchText = ref('')

const form = reactive({
  categoryId: '',
  title: '',
  content: '',
  answer: '',
  difficulty: 1,
})

const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '分类', key: 'category', width: 120 },
  { title: '难度', key: 'difficulty', width: 90, align: 'center' as const },
  { title: '操作', key: 'action', width: 150, align: 'center' as const },
]

const filteredQuestions = computed(() => {
  let list = questions.value
  if (filterCategory.value) list = list.filter((q) => q.categoryId === filterCategory.value)
  if (filterDifficulty.value) list = list.filter((q) => q.difficulty === filterDifficulty.value)
  if (searchText.value) {
    const kw = searchText.value.toLowerCase()
    list = list.filter((q) => q.title.toLowerCase().includes(kw))
  }
  return list
})

function categoryName(id: string) {
  return categories.value.find((c) => c.id === id)?.name || id
}

function difficultyLabel(d: number) {
  return ['', '简单', '中等', '困难', '专家', '地狱'][d] || `${d}`
}

function resetForm() {
  editingId.value = ''
  showModal.value = false
  form.categoryId = categories.value[0]?.id || ''
  form.title = ''
  form.content = ''
  form.answer = ''
  form.difficulty = 1
}

function editQuestion(q: QuestionItem) {
  editingId.value = q.id
  form.categoryId = q.categoryId
  form.title = q.title
  form.content = q.content
  form.answer = q.answer || ''
  form.difficulty = q.difficulty
  showModal.value = true
}

async function load() {
  loading.value = true
  try {
    const [cats, qs] = await Promise.all([api.getCategories(), api.getQuestions()])
    categories.value = cats
    questions.value = qs
  } finally {
    loading.value = false
  }
}

async function saveQuestion() {
  if (!form.categoryId || !form.title.trim() || !form.content.trim()) {
    message.warning('请填写必填项')
    return
  }
  try {
    const body = {
      categoryId: form.categoryId,
      title: form.title.trim(),
      content: form.content.trim(),
      answer: form.answer.trim() || null,
      difficulty: form.difficulty,
      source: 'MANUAL',
      sourceUrl: null,
    }
    if (editingId.value) {
      await api.updateQuestion(editingId.value, body)
      message.success('题目已更新')
    } else {
      await api.createQuestion(body)
      message.success('题目已创建')
    }
    resetForm()
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function removeQuestion(id: string) {
  try {
    await api.deleteQuestion(id)
    message.success('题目已删除')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
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
</style>
