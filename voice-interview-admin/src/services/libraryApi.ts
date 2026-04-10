import type { CategoryItem, QuestionItem, ImportTaskItem, ImportTextResult } from '@/types'
import { request, jsonPost, jsonPut, del } from './http'

// --- Categories ---
export const getCategories = () => request<CategoryItem[]>('/api/library/categories')

export const createCategory = (body: { name: string; parentId: string; sortOrder: number }) =>
  jsonPost<CategoryItem>('/api/library/categories', body)

export const updateCategory = (id: string, body: { name: string; parentId: string; sortOrder: number }) =>
  jsonPut<CategoryItem>(`/api/library/categories/${id}`, body)

export const deleteCategory = (id: string) => del<void>(`/api/library/categories/${id}`)

// --- Questions ---
export const getQuestions = () => request<QuestionItem[]>('/api/library/questions')
  .then((items) => items.map((item) => ({
    ...item,
    tags: item.tags ?? [],
  })))

export const createQuestion = (body: Partial<QuestionItem>) =>
  jsonPost<QuestionItem>('/api/library/questions', body)

export const updateQuestion = (id: string, body: Partial<QuestionItem>) =>
  jsonPut<QuestionItem>(`/api/library/questions/${id}`, body)

export const deleteQuestion = (id: string) => del<void>(`/api/library/questions/${id}`)

// --- Import ---
export const getImportTasks = () => request<ImportTaskItem[]>('/api/library/imports')

export const importText = (body: { categoryId: string; rawText: string; fileName: string }) =>
  jsonPost<ImportTextResult>('/api/library/imports/text', body)
