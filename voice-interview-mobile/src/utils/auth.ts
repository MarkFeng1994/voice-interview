import type { useUserStore } from '@/stores/user'

export const TOKEN_STORAGE_KEY = 'voice-interview:token'

export const getStoredToken = () => {
  try {
    return uni.getStorageSync(TOKEN_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

export const buildAuthHeader = (): Record<string, string> => {
  const token = getStoredToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const redirectToLogin = (redirectUrl?: string) => {
  const url = redirectUrl
    ? `/pages/login/index?redirect=${encodeURIComponent(redirectUrl)}`
    : '/pages/login/index'
  uni.reLaunch({ url })
}

export const ensureAuthenticated = (userStore: ReturnType<typeof useUserStore>, redirectUrl?: string) => {
  if (userStore.isLoggedIn) {
    return true
  }
  redirectToLogin(redirectUrl)
  return false
}
