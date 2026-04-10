import { buildAuthHeader } from '@/utils/auth'
import type { AsrResponse, MediaUploadResponse, ResumeProfileResponse } from '@/types/interview'

export const uploadMediaByFilePath = async (
  apiBaseUrl: string,
  filePath: string,
  fileName = 'recording.wav',
) => {
  const uploadResult = await uni.uploadFile({
    url: `${apiBaseUrl}/api/media/upload`,
    filePath,
    name: 'file',
    fileType: 'audio',
    header: buildAuthHeader(),
    formData: {
      fileName,
    },
  })

  return JSON.parse(uploadResult.data) as MediaUploadResponse
}

export const uploadMediaByBrowserFile = async (apiBaseUrl: string, file: File) => {
  const formData = new FormData()
  formData.append('file', file, file.name)
  const response = await fetch(`${apiBaseUrl}/api/media/upload`, {
    method: 'POST',
    headers: buildAuthHeader(),
    body: formData,
  })
  if (!response.ok) {
    throw new Error(`上传失败，HTTP ${response.status}`)
  }
  return await response.json() as MediaUploadResponse
}

export const transcribeMedia = async (apiBaseUrl: string, fileId: string) => {
  const response = await uni.request({
    url: `${apiBaseUrl}/api/asr/transcriptions/${fileId}`,
    method: 'POST',
    header: buildAuthHeader(),
  })
  return response.data as AsrResponse
}

export const uploadResumeByFilePath = async (
  apiBaseUrl: string,
  filePath: string,
  fileName = 'resume.pdf',
) => {
  const uploadResult = await uni.uploadFile({
    url: `${apiBaseUrl}/api/resumes/upload`,
    filePath,
    name: 'file',
    fileType: 'video' as any, // uni-app fileType enum lacks 'document'; 'video' bypasses runtime checks
    header: buildAuthHeader(),
    formData: { fileName },
  })
  return JSON.parse(uploadResult.data) as ResumeProfileResponse
}

export const uploadResumeByBrowserFile = async (apiBaseUrl: string, file: File) => {
  const formData = new FormData()
  formData.append('file', file, file.name)
  const response = await fetch(`${apiBaseUrl}/api/resumes/upload`, {
    method: 'POST',
    headers: buildAuthHeader(),
    body: formData,
  })
  if (!response.ok) {
    throw new Error(`上传失败，HTTP ${response.status}`)
  }
  return (await response.json()) as ResumeProfileResponse
}

export const getResumeProfile = async (apiBaseUrl: string, resumeId: string) => {
  const response = await uni.request({
    url: `${apiBaseUrl}/api/resumes/${resumeId}`,
    method: 'GET',
    header: buildAuthHeader(),
  })
  return response.data as ResumeProfileResponse
}

export const reparseResumeProfile = async (apiBaseUrl: string, resumeId: string) => {
  const response = await uni.request({
    url: `${apiBaseUrl}/api/resumes/${resumeId}/reparse`,
    method: 'POST',
    header: buildAuthHeader(),
  })
  return response.data as ResumeProfileResponse
}
