const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

const resolveApiBaseUrl = () => {
  const envBaseUrl = import.meta.env.VITE_API_BASE_URL
  if (envBaseUrl) {
    return trimTrailingSlash(envBaseUrl)
  }

  // #ifdef H5
  return `${window.location.protocol}//${window.location.hostname}:8080`
  // #endif

  return 'http://127.0.0.1:8080'
}

export const API_BASE_URL = resolveApiBaseUrl()

export const toAbsoluteUrl = (value: string) => {
  if (/^https?:\/\//.test(value)) {
    return value
  }
  return `${API_BASE_URL}${value.startsWith('/') ? '' : '/'}${value}`
}

export const toWebSocketUrl = (baseUrl: string, path: string) => {
  const normalizedBase = baseUrl.replace(/^http/, 'ws').replace(/^https/, 'wss').replace(/\/+$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}
