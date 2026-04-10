import type { LoginPayload, UserProfile } from '@/types'
import { request, jsonPost } from './http'

export function login(username: string, password: string) {
  return jsonPost<LoginPayload>('/api/auth/login', { username, password })
}

export function getProfile() {
  return request<UserProfile>('/api/user/profile')
}
