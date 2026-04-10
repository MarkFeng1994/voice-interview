import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserProfile } from '@/types'
import { setToken, getToken } from '@/services/http'
import * as authApi from '@/services/authApi'

const STORAGE_PROFILE = 'voice-interview-admin:profile'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const profile = ref<UserProfile | null>(
    localStorage.getItem(STORAGE_PROFILE)
      ? (JSON.parse(localStorage.getItem(STORAGE_PROFILE)!) as UserProfile)
      : null,
  )

  const isLoggedIn = computed(() => Boolean(token.value && profile.value))

  async function login(username: string, password: string) {
    const payload = await authApi.login(username, password)
    token.value = payload.token
    profile.value = payload.profile
    setToken(payload.token)
    localStorage.setItem(STORAGE_PROFILE, JSON.stringify(payload.profile))
  }

  async function loadProfile() {
    profile.value = await authApi.getProfile()
    localStorage.setItem(STORAGE_PROFILE, JSON.stringify(profile.value))
  }

  function logout() {
    token.value = ''
    profile.value = null
    setToken('')
    localStorage.removeItem(STORAGE_PROFILE)
  }

  return { token, profile, isLoggedIn, login, loadProfile, logout }
})
