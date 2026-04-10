export interface UserProfile {
  id: string
  username: string
  nickname: string
}

export interface LoginPayload {
  token: string
  expiresIn: number
  profile: UserProfile
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
}

export interface UpdateProfileRequest {
  nickname: string
}
