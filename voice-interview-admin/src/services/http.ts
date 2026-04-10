import type { ApiEnvelope } from '@/types'

const STORAGE_API_BASE = 'voice-interview-admin:api-base'
const STORAGE_TOKEN = 'voice-interview-admin:token'

export function getApiBase(): string {
  return localStorage.getItem(STORAGE_API_BASE) || 'http://127.0.0.1:8080'
}

export function setApiBase(url: string) {
  localStorage.setItem(STORAGE_API_BASE, url)
}

export function getToken(): string {
  return localStorage.getItem(STORAGE_TOKEN) || ''
}

export function setToken(token: string) {
  if (token) {
    localStorage.setItem(STORAGE_TOKEN, token)
  } else {
    localStorage.removeItem(STORAGE_TOKEN)
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const base = getApiBase().replace(/\/+$/, '')
  const token = getToken()

  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers || {}),
    },
  })

  const payload = (await response.json()) as ApiEnvelope<T>

  if (!response.ok || !payload.success) {
    if (response.status === 401) {
      setToken('')
      localStorage.removeItem('voice-interview-admin:profile')
      window.location.hash = '#/login'
    }
    throw new Error(payload.message || `Request failed (${response.status})`)
  }

  return payload.data
}

export function jsonPost<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function jsonPut<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}
