import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fetchProfile, loginUser, registerUser, updateProfile } from '@/services/authApi'
import type { LoginRequest, RegisterRequest, UpdateProfileRequest, UserProfile } from '@/types/user'
import { TOKEN_STORAGE_KEY } from '@/utils/auth'

const USER_STORAGE_KEY = 'voice-interview:user'

const readStorage = (key: string) => {
  try {
    return uni.getStorageSync(key)
  } catch {
    return ''
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(readStorage(TOKEN_STORAGE_KEY) || '')
  const profile = ref<UserProfile | null>(readStorage(USER_STORAGE_KEY) || null)
  const bootstrapped = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value && profile.value))

  const persist = () => {
    if (token.value) {
      uni.setStorageSync(TOKEN_STORAGE_KEY, token.value)
    } else {
      uni.removeStorageSync(TOKEN_STORAGE_KEY)
    }

    if (profile.value) {
      uni.setStorageSync(USER_STORAGE_KEY, profile.value)
    } else {
      uni.removeStorageSync(USER_STORAGE_KEY)
    }
  }

  const applyLogin = (nextToken: string, nextProfile: UserProfile) => {
    token.value = nextToken
    profile.value = nextProfile
    persist()
  }

  const login = async (payload: LoginRequest) => {
    const response = await loginUser(payload)
    if (!response.success) {
      throw new Error(response.message || '登录失败')
    }
    applyLogin(response.data.token, response.data.profile)
    return response.data
  }

  const register = async (payload: RegisterRequest) => {
    const response = await registerUser(payload)
    if (!response.success) {
      throw new Error(response.message || '注册失败')
    }
    applyLogin(response.data.token, response.data.profile)
    return response.data
  }

  const refreshProfile = async () => {
    if (!token.value) {
      throw new Error('当前没有登录 token')
    }
    const response = await fetchProfile(token.value)
    if (!response.success) {
      throw new Error(response.message || '获取用户信息失败')
    }
    profile.value = response.data
    persist()
    return response.data
  }

  const saveProfile = async (payload: UpdateProfileRequest) => {
    if (!token.value) {
      throw new Error('当前没有登录 token')
    }
    const response = await updateProfile(token.value, payload)
    if (!response.success) {
      throw new Error(response.message || '更新用户信息失败')
    }
    profile.value = response.data
    persist()
    return response.data
  }

  const bootstrap = async () => {
    if (bootstrapped.value) {
      return
    }
    if (!token.value) {
      bootstrapped.value = true
      return
    }
    try {
      await refreshProfile()
    } catch {
      logout()
    } finally {
      bootstrapped.value = true
    }
  }

  const logout = () => {
    token.value = ''
    profile.value = null
    persist()
  }

  return {
    token,
    profile,
    isLoggedIn,
    bootstrapped,
    bootstrap,
    login,
    register,
    refreshProfile,
    saveProfile,
    logout,
  }
})
