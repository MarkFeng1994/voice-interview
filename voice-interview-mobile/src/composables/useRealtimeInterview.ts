import { onUnmounted, ref } from 'vue'
import { toWebSocketUrl } from '@/config/api'
import { issueRealtimeWsTicket } from '@/services/interviewApi'

interface UseRealtimeInterviewOptions {
  apiBaseUrl: string
  onUserTranscript: (text: string) => void
  onAssistantTranscript: (text: string) => void
  onSessionUpdated: (session: any) => void
  onStatusChange: (status: string) => void
  onError: (error: string) => void
  onFallbackToHalfDuplex: (reason: string, session: any) => void
}

export const useRealtimeInterview = (options: UseRealtimeInterviewOptions) => {
  const isConnected = ref(false)
  const isAiSpeaking = ref(false)
  const canInterrupt = ref(false)

  let socket: WebSocket | null = null
  let audioContext: AudioContext | null = null
  let audioWorklet: AudioWorkletNode | null = null
  let mediaStream: MediaStream | null = null
  let currentSource: AudioBufferSourceNode | null = null
  const audioQueue: AudioBuffer[] = []

  // Check browser capability for realtime mode
  const checkRealtimeCapability = (): { available: boolean; reason?: string } => {
    if (typeof AudioContext === 'undefined') {
      return { available: false, reason: '浏览器不支持 AudioContext' }
    }
    if (typeof AudioWorkletNode === 'undefined') {
      return { available: false, reason: '浏览器不支持 AudioWorklet' }
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      return { available: false, reason: '浏览器不支持麦克风访问' }
    }
    if (typeof WebSocket === 'undefined') {
      return { available: false, reason: '浏览器不支持 WebSocket' }
    }
    return { available: true }
  }

  // Connect to realtime WebSocket
  const connect = async (sessionId: string) => {
    // Pre-flight capability check
    const cap = checkRealtimeCapability()
    if (!cap.available) {
      options.onFallbackToHalfDuplex(cap.reason!, null)
      return
    }

    try {
      // Issue realtime ticket
      const ticketResp = await issueRealtimeWsTicket(options.apiBaseUrl, sessionId)
      const ticket = ticketResp.data.ticket

      // Build WebSocket URL
      const wsUrl = toWebSocketUrl(options.apiBaseUrl, `/ws/realtime?sessionId=${sessionId}&ticket=${ticket}`)

      // Create WebSocket
      socket = new WebSocket(wsUrl)

      socket.onopen = () => {
        isConnected.value = true
        options.onStatusChange('已连接全双工通道')
        startAudioCapture()
      }

      socket.onmessage = (event) => {
        const msg = JSON.parse(event.data)
        handleServerMessage(msg)
      }

      socket.onerror = () => {
        options.onError('WebSocket 连接失败')
      }

      socket.onclose = () => {
        isConnected.value = false
        stopAudioCapture()
        options.onStatusChange('全双工通道已断开')
      }
    } catch (error: any) {
      options.onError(error.message || '连接失败')
    }
  }

  // Start audio capture
  const startAudioCapture = async () => {
    try {
      // Request microphone
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true
        }
      })

      // Create AudioContext
      audioContext = new AudioContext({ sampleRate: 16000 })
      await audioContext.audioWorklet.addModule('/audio-processor.js')

      const source = audioContext.createMediaStreamSource(mediaStream)
      audioWorklet = new AudioWorkletNode(audioContext, 'pcm-processor')

      // Listen for PCM chunks
      audioWorklet.port.onmessage = (e) => {
        const pcmChunk = e.data as Float32Array
        sendAudioChunk(pcmChunk)
      }

      source.connect(audioWorklet)
    } catch (error: any) {
      options.onError('麦克风访问失败: ' + error.message)
    }
  }

  // Stop audio capture
  const stopAudioCapture = () => {
    mediaStream?.getTracks().forEach(t => t.stop())
    audioWorklet?.disconnect()
    audioContext?.close()
    mediaStream = null
    audioWorklet = null
    audioContext = null
  }

  // Send audio chunk to server
  const sendAudioChunk = (pcm: Float32Array) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return

    // Convert Float32 to Int16 PCM
    const int16 = new Int16Array(pcm.length)
    for (let i = 0; i < pcm.length; i++) {
      int16[i] = Math.max(-32768, Math.min(32767, pcm[i] * 32768))
    }

    // Convert to Base64
    const base64 = arrayBufferToBase64(int16.buffer)

    socket.send(JSON.stringify({
      type: 'audio.append',
      audio: base64
    }))
  }

  // Handle server messages
  const handleServerMessage = (msg: any) => {
    switch (msg.type) {
      case 'audio.delta':
        // AI audio stream
        playAudioDelta(msg.audio)
        isAiSpeaking.value = true
        canInterrupt.value = true
        break

      case 'audio.done':
        isAiSpeaking.value = false
        canInterrupt.value = false
        break

      case 'transcript.user':
        // User speech transcript
        options.onUserTranscript(msg.text)
        break

      case 'transcript.assistant':
        // AI response transcript
        options.onAssistantTranscript(msg.text)
        break

      case 'session.updated':
        // Interview state update
        options.onSessionUpdated(msg.session)
        break

      case 'mode.changed':
        // Backend triggered fallback
        if (msg.mode === 'standard') {
          options.onFallbackToHalfDuplex(msg.reason, msg.session)
        }
        break

      case 'realtime.reconnecting':
        options.onStatusChange(`重连中 (${msg.attempt}/${msg.maxAttempt})...`)
        break

      case 'realtime.reconnected':
        options.onStatusChange('已恢复实时连接')
        break

      case 'error':
        options.onError(msg.message || '发生错误')
        break
    }
  }

  // Play AI audio delta
  const playAudioDelta = (base64Audio: string) => {
    if (!audioContext) return

    const pcmBytes = base64ToInt16Array(base64Audio)
    const float32 = new Float32Array(pcmBytes.length)
    for (let i = 0; i < pcmBytes.length; i++) {
      float32[i] = pcmBytes[i] / 32768
    }

    const buffer = audioContext.createBuffer(1, float32.length, 24000)
    buffer.copyToChannel(float32, 0)

    audioQueue.push(buffer)
    if (!currentSource) {
      playNextInQueue()
    }
  }

  // Play next audio buffer in queue
  const playNextInQueue = () => {
    if (audioQueue.length === 0) {
      currentSource = null
      return
    }

    const buffer = audioQueue.shift()!
    currentSource = audioContext!.createBufferSource()
    currentSource.buffer = buffer
    currentSource.connect(audioContext!.destination)
    currentSource.onended = () => playNextInQueue()
    currentSource.start()
  }

  // User interrupts AI
  const interrupt = () => {
    if (currentSource) {
      currentSource.stop()
      currentSource = null
      audioQueue.length = 0
    }

    socket?.send(JSON.stringify({
      type: 'conversation.interrupt'
    }))

    isAiSpeaking.value = false
    canInterrupt.value = false
  }

  // Disconnect
  const disconnect = () => {
    stopAudioCapture()
    socket?.close()
    socket = null
    isConnected.value = false
  }

  // Cleanup on unmount
  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    isAiSpeaking,
    canInterrupt,
    connect,
    disconnect,
    interrupt,
    checkRealtimeCapability
  }
}

// Helper: ArrayBuffer to Base64
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

// Helper: Base64 to Int16Array
function base64ToInt16Array(base64: string): Int16Array {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Int16Array(bytes.buffer)
}
