import { API_BASE_URL } from '@/config/api'
import type { ApiEnvelope } from '@/types/interview'
import type {
  LoginPayload,
  LoginRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserProfile,
} from '@/types/user'

const request = async <T>(
  url: string,
  method: 'GET' | 'POST' | 'PUT',
  data?: UniApp.RequestOptions['data'],
  header?: Record<string, string>,
) => {
  const response = await uni.request({
    url,
    method,
    data,
    header,
  })
  return response.data as T
}

export const registerUser = (payload: RegisterRequest) =>
  request<ApiEnvelope<LoginPayload>>(
    `${API_BASE_URL}/api/auth/register`,
    'POST',
    payload,
    { 'Content-Type': 'application/json' },
  )

export const loginUser = (payload: LoginRequest) =>
  request<ApiEnvelope<LoginPayload>>(
    `${API_BASE_URL}/api/auth/login`,
    'POST',
    payload,
    { 'Content-Type': 'application/json' },
  )

export const fetchProfile = (token: string) =>
  request<ApiEnvelope<UserProfile>>(
    `${API_BASE_URL}/api/user/profile`,
    'GET',
    undefined,
    { Authorization: `Bearer ${token}` },
  )

export const updateProfile = (token: string, payload: UpdateProfileRequest) =>
  request<ApiEnvelope<UserProfile>>(
    `${API_BASE_URL}/api/user/profile`,
    'PUT',
    payload,
    {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  )
