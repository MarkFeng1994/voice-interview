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
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  const maxReconnectAttempts = 2

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

  const clearReconnectTimer = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const scheduleReconnect = () => {
    if (!activeSessionId || reconnectAttempts >= maxReconnectAttempts) {
      options.onStatusChange('实时通道异常', 'WebSocket 连接失败，可手动重试或直接文本作答。')
      return
    }

    const nextAttempt = reconnectAttempts + 1
    const delay = nextAttempt === 1 ? 0 : 1500
    reconnectAttempts = nextAttempt
    clearReconnectTimer()
    reconnectTimer = setTimeout(() => {
      connect(activeSessionId, true).catch(() => {
        options.onStatusChange('实时通道异常', 'WebSocket 连接失败，可手动重试或直接文本作答。')
      })
    }, delay)
  }

  const disconnect = () => {
    clearReconnectTimer()
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

  const connect = async (sessionId: string, isReconnect = false) => {
    if (!sessionId) {
      return
    }

    if (socketTask && activeSessionId === sessionId && socketStatus.value === 'OPEN') {
      return
    }

    if (socketTask) {
      disconnect()
    }

    if (!isReconnect) {
      reconnectAttempts = 0
    }

    socketStatus.value = 'CONNECTING'
    options.onStatusChange(
      isReconnect ? '重连实时通道' : '连接实时通道',
      isReconnect ? '正在尝试重新连接面试 WebSocket 通道。' : '正在建立面试 WebSocket 通道。',
    )

    const ticketPayload = await issueInterviewWsTicket(options.apiBaseUrl, sessionId)
    if (!ticketPayload.success) {
      scheduleReconnect()
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
        reconnectAttempts = 0
        clearReconnectTimer()
        options.onStatusChange('实时通道已连接', '会话更新会自动推送到当前页面。')
        sendSyncRequest()
      }
      nativeSocket.onmessage = (event) => {
        handleMessage(event.data)
      }
      nativeSocket.onclose = () => {
        socketStatus.value = 'CLOSED'
        scheduleReconnect()
      }
      nativeSocket.onerror = () => {
        socketStatus.value = 'ERROR'
        scheduleReconnect()
      }
      return
    }

    const connectedSocketTask = await uni.connectSocket({ url: socketUrl })
    socketTask = connectedSocketTask

    connectedSocketTask.onOpen(() => {
      socketStatus.value = 'OPEN'
      reconnectAttempts = 0
      clearReconnectTimer()
      options.onStatusChange('实时通道已连接', '会话更新会自动推送到当前页面。')
      sendSyncRequest()
    })

    connectedSocketTask.onMessage((event: UniApp.OnSocketMessageCallbackResult) => {
      handleMessage(event.data)
    })

    connectedSocketTask.onClose(() => {
      socketStatus.value = 'CLOSED'
      scheduleReconnect()
    })

    connectedSocketTask.onError(() => {
      socketStatus.value = 'ERROR'
      scheduleReconnect()
    })
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    socketStatus,
    connect,
    disconnect,
    reconnect: (sessionId?: string) => connect(sessionId || activeSessionId),
  }
}
