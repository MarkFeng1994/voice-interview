<template>
  <div>
    <div class="page-header">
      <h1>分类管理</h1>
      <p>管理题目分类，支持父子层级结构</p>
    </div>

    <a-row :gutter="[20, 20]">
      <a-col :xs="24" :md="10">
        <div class="section-card">
          <h3><PlusOutlined /> {{ editingId ? '编辑分类' : '新建分类' }}</h3>
          <a-form layout="vertical" @finish="saveCategory">
            <a-form-item label="分类名称">
              <a-input v-model:value="form.name" placeholder="如：Java 基础、缓存、微服务" />
            </a-form-item>
            <a-form-item label="父级分类">
              <a-select v-model:value="form.parentId">
                <a-select-option value="0">顶级分类</a-select-option>
                <a-select-option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="排序值">
              <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
            </a-form-item>
            <a-space>
              <a-button type="primary" html-type="submit">{{ editingId ? '更新' : '创建' }}</a-button>
              <a-button v-if="editingId" @click="resetForm">取消编辑</a-button>
            </a-space>
          </a-form>
        </div>
      </a-col>

      <a-col :xs="24" :md="14">
        <div class="section-card">
          <h3><AppstoreOutlined /> 分类列表</h3>
          <a-spin :spinning="loading">
            <a-empty v-if="!categories.length" description="暂无分类" />
            <div v-else class="cat-list">
              <div v-for="c in categories" :key="c.id" class="cat-item">
                <div class="cat-info">
                  <strong>{{ c.name }}</strong>
                  <span class="cat-meta">
                    {{ c.parentId === '0' ? '顶级' : parentName(c.parentId) }}
                    &middot; 排序 {{ c.sortOrder }}
                  </span>
                </div>
                <a-space>
                  <a-button size="small" @click="editCategory(c)">编辑</a-button>
                  <a-popconfirm title="确认删除该分类？" @confirm="removeCategory(c.id)">
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
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
import { PlusOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import * as api from '@/services/libraryApi'
import type { CategoryItem } from '@/types'

const loading = ref(false)
const categories = ref<CategoryItem[]>([])
const editingId = ref('')

const form = reactive({ name: '', parentId: '0', sortOrder: 0 })

function parentName(pid: string) {
  return categories.value.find((c) => c.id === pid)?.name || pid
}

function resetForm() {
  editingId.value = ''
  form.name = ''
  form.parentId = '0'
  form.sortOrder = 0
}

function editCategory(c: CategoryItem) {
  editingId.value = c.id
  form.name = c.name
  form.parentId = c.parentId
  form.sortOrder = c.sortOrder
}

async function load() {
  loading.value = true
  try {
    categories.value = await api.getCategories()
  } finally {
    loading.value = false
  }
}

async function saveCategory() {
  if (!form.name.trim()) { message.warning('名称不能为空'); return }
  try {
    const body = { name: form.name.trim(), parentId: form.parentId || '0', sortOrder: form.sortOrder }
    if (editingId.value) {
      await api.updateCategory(editingId.value, body)
      message.success('分类已更新')
    } else {
      await api.createCategory(body)
      message.success('分类已创建')
    }
    resetForm()
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function removeCategory(id: string) {
  try {
    await api.deleteCategory(id)
    message.success('分类已删除')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.cat-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 8px;
  transition: background var(--transition);
}

.cat-item:hover {
  background: var(--bg-hover);
}

.cat-info strong {
  display: block;
  font-size: 14px;
}

.cat-meta {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
