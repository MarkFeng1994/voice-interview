import { onUnmounted, ref } from 'vue'

import { toWebSocketUrl } from '@/config/api'
import { issueInterviewWsTicket } from '@/services/interviewApi'
import type { InterviewSessionState, InterviewSocketMessage } from '@/types/interview'

interface UseInterviewSocketOptions {
  apiBaseUrl: string
  onSessionMessage: (session: InterviewSessionState, eventType: string) => void
  onStatusChange: (status: string, hint: string) => void
}

export const useInterviewSocket = (options: UseInterviewSocketOptions) => {
  const socketStatus = ref<'IDLE' | 'CONNECTING' | 'OPEN' | 'CLOSED' | 'ERROR'>('IDLE')
  let socketTask: UniApp.SocketTask | WebSocket | null = null
  let activeSessionId = ''

  const isNativeWebSocket = (value: unknown): value is WebSocket =>
    typeof WebSocket !== 'undefined' && value instanceof WebSocket

  const sendSyncRequest = () => {
    const payload = JSON.stringify({ type: 'SYNC_REQUEST' })
    if (isNativeWebSocket(socketTask)) {
      socketTask.send(payload)
      return
    }
    socketTask?.send({ data: payload })
  }

  const disconnect = () => {
    if (socketTask) {
      if (isNativeWebSocket(socketTask)) {
        socketTask.close()
      } else {
        socketTask.close({})
      }
      socketTask = null
    }
    activeSessionId = ''
    socketStatus.value = 'CLOSED'
  }

  const connect = async (sessionId: string) => {
    if (!sessionId) {
      return
    }

    if (socketTask && activeSessionId === sessionId && socketStatus.value === 'OPEN') {
      return
    }

    if (socketTask) {
      disconnect()
    }

    socketStatus.value = 'CONNECTING'
    options.onStatusChange('连接实时通道', '正在建立面试 WebSocket 通道。')

    const ticketPayload = await issueInterviewWsTicket(options.apiBaseUrl, sessionId)
    if (!ticketPayload.success) {
      throw new Error(ticketPayload.message || '获取实时通道 ticket 失败')
    }

    const socketUrl = toWebSocketUrl(options.apiBaseUrl, `/ws/interview?ticket=${encodeURIComponent(ticketPayload.data.ticket)}`)
    activeSessionId = sessionId

    const handleMessage = (raw: unknown) => {
      try {
        const payload = JSON.parse(String(raw)) as InterviewSocketMessage
        if ((payload.type === 'SESSION_SNAPSHOT' || payload.type === 'SESSION_UPDATED') && payload.session) {
          options.onSessionMessage(payload.session, payload.type)
        }
      } catch (error) {
        options.onStatusChange('实时消息异常', error instanceof Error ? error.message : '无法解析实时消息。')
      }
    }

    if (typeof window !== 'undefined' && typeof WebSocket !== 'undefined') {
      const nativeSocket = new WebSocket(socketUrl)
      socketTask = nativeSocket
      nativeSocket.onopen = () => {
        socketStatus.value = 'OPEN'
        options.onStatusChange('实时通道已连接', '会话更新会自动推送到当前页面。')
        sendSyncRequest()
      }
      nativeSocket.onmessage = (event) => {
        handleMessage(event.data)
      }
      nativeSocket.onclose = () => {
        socketStatus.value = 'CLOSED'
      }
      nativeSocket.onerror = () => {
        socketStatus.value = 'ERROR'
        options.onStatusChange('实时通道异常', 'WebSocket 连接失败，可继续使用手动同步。')
      }
      return
    }

    const connectedSocketTask = await uni.connectSocket({ url: socketUrl })
    socketTask = connectedSocketTask

    connectedSocketTask.onOpen(() => {
      socketStatus.value = 'OPEN'
      options.onStatusChange('实时通道已连接', '会话更新会自动推送到当前页面。')
      sendSyncRequest()
    })

    connectedSocketTask.onMessage((event: UniApp.OnSocketMessageCallbackResult) => {
      handleMessage(event.data)
    })

    connectedSocketTask.onClose(() => {
      socketStatus.value = 'CLOSED'
    })

    connectedSocketTask.onError(() => {
      socketStatus.value = 'ERROR'
      options.onStatusChange('实时通道异常', 'WebSocket 连接失败，可继续使用手动同步。')
    })
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    socketStatus,
    connect,
    disconnect,
  }
}
