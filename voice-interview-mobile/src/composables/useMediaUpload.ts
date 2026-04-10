import { computed, onMounted, onUnmounted, ref } from 'vue'

import {
  transcribeMedia,
  uploadMediaByBrowserFile,
  uploadMediaByFilePath,
} from '@/services/mediaApi'
import type { InterviewMessage } from '@/types/interview'
import { formatDurationMs } from '@/utils/time'

interface UseMediaUploadOptions {
  apiBaseUrl: string
  toAbsoluteUrl: (value: string) => string
  onStatusChange: (status: string, hint: string) => void
  appendMessage: (message: InterviewMessage) => void
  onTranscriptReady?: (transcript: string) => void
  onAudioSamples?: (samples: Float32Array) => void
}

export const useMediaUpload = (options: UseMediaUploadOptions) => {
  const recorderManager = typeof uni.getRecorderManager === 'function'
    ? uni.getRecorderManager()
    : null

  let browserStream: MediaStream | null = null
  let browserAudioContext: AudioContext | null = null
  let browserSourceNode: MediaStreamAudioSourceNode | null = null
  let browserProcessorNode: ScriptProcessorNode | null = null
  let browserGainNode: GainNode | null = null
  let browserBuffers: Float32Array[] = []
  let browserSampleRate = 16_000
  let browserRecordingStartedAt = 0

  const isRecording = ref(false)
  const isUploading = ref(false)
  const lastAudioUrl = ref('')
  const lastUploadedFileId = ref('')
  const lastTranscript = ref('')
  const lastAudioDurationMs = ref(0)

  const latestAudioLabel = computed(() =>
    lastAudioUrl.value ? '点击“回放最新”可验证上传后的音频链路。' : '还没有上传过新的语音文件。'
  )

  onMounted(() => {
    if (!recorderManager) {
      return
    }

    recorderManager.onStart(() => {
      isRecording.value = true
      options.onStatusChange('录音中', '松开按钮后会自动上传录音文件。')
    })

    recorderManager.onStop(async (result) => {
      isRecording.value = false
      await uploadAudio(result.tempFilePath, result.duration)
    })

    recorderManager.onError((error) => {
      isRecording.value = false
      const message = typeof error.errMsg === 'string' ? error.errMsg : '请检查录音权限或改用选择音频。'
      options.onStatusChange('录音失败', message)
    })
  })

  onUnmounted(() => {
    void stopBrowserRecording(true)
  })

  const canUseBrowserRecorder = () =>
    typeof window !== 'undefined'
    && typeof navigator !== 'undefined'
    && Boolean(navigator.mediaDevices?.getUserMedia)
    && typeof AudioContext !== 'undefined'

  const mergeAudioBuffers = (buffers: Float32Array[]) => {
    const totalLength = buffers.reduce((sum, item) => sum + item.length, 0)
    const merged = new Float32Array(totalLength)
    let offset = 0
    for (const buffer of buffers) {
      merged.set(buffer, offset)
      offset += buffer.length
    }
    return merged
  }

  const encodeWav = (samples: Float32Array, sampleRate: number) => {
    const bytesPerSample = 2
    const blockAlign = bytesPerSample
    const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample)
    const view = new DataView(buffer)

    const writeString = (offset: number, value: string) => {
      for (let index = 0; index < value.length; index += 1) {
        view.setUint8(offset + index, value.charCodeAt(index))
      }
    }

    writeString(0, 'RIFF')
    view.setUint32(4, 36 + samples.length * bytesPerSample, true)
    writeString(8, 'WAVE')
    writeString(12, 'fmt ')
    view.setUint32(16, 16, true)
    view.setUint16(20, 1, true)
    view.setUint16(22, 1, true)
    view.setUint32(24, sampleRate, true)
    view.setUint32(28, sampleRate * blockAlign, true)
    view.setUint16(32, blockAlign, true)
    view.setUint16(34, 16, true)
    writeString(36, 'data')
    view.setUint32(40, samples.length * bytesPerSample, true)

    let offset = 44
    for (let index = 0; index < samples.length; index += 1) {
      const sample = Math.max(-1, Math.min(1, samples[index]))
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
      offset += bytesPerSample
    }

    return new Blob([buffer], { type: 'audio/wav' })
  }

  const cleanupBrowserNodes = async () => {
    if (browserProcessorNode) {
      browserProcessorNode.disconnect()
      browserProcessorNode.onaudioprocess = null
      browserProcessorNode = null
    }

    if (browserSourceNode) {
      browserSourceNode.disconnect()
      browserSourceNode = null
    }

    if (browserGainNode) {
      browserGainNode.disconnect()
      browserGainNode = null
    }

    if (browserStream) {
      browserStream.getTracks().forEach((track) => track.stop())
      browserStream = null
    }

    if (browserAudioContext) {
      await browserAudioContext.close()
      browserAudioContext = null
    }
  }

  const startBrowserRecording = async () => {
    try {
      browserStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      browserAudioContext = new AudioContext()
      browserSampleRate = browserAudioContext.sampleRate
      browserSourceNode = browserAudioContext.createMediaStreamSource(browserStream)
      browserProcessorNode = browserAudioContext.createScriptProcessor(4096, 1, 1)
      browserGainNode = browserAudioContext.createGain()
      browserGainNode.gain.value = 0
      browserBuffers = []
      browserRecordingStartedAt = Date.now()

      browserProcessorNode.onaudioprocess = (event) => {
        const channelData = event.inputBuffer.getChannelData(0)
        browserBuffers.push(new Float32Array(channelData))
        options.onAudioSamples?.(channelData)
      }

      browserSourceNode.connect(browserProcessorNode)
      browserProcessorNode.connect(browserGainNode)
      browserGainNode.connect(browserAudioContext.destination)

      isRecording.value = true
      options.onStatusChange('录音中', '松开按钮后会自动上传录音文件。')
    } catch (error) {
      await cleanupBrowserNodes()
      const message = error instanceof Error ? error.message : '当前浏览器无法访问麦克风，请检查权限。'
      options.onStatusChange('无法开始录音', message)
    }
  }

  const stopBrowserRecording = async (silent = false) => {
    if (!isRecording.value && !silent) {
      return
    }

    const durationMs = Math.max(0, Date.now() - browserRecordingStartedAt)
    const samples = mergeAudioBuffers(browserBuffers)
    browserBuffers = []
    isRecording.value = false

    await cleanupBrowserNodes()

    if (silent || samples.length === 0) {
      if (!silent) {
        options.onStatusChange('录音失败', '没有采集到有效音频，请重试。')
      }
      return
    }

    const wavBlob = encodeWav(samples, browserSampleRate)
    const wavFile = new File([wavBlob], 'recording.wav', { type: 'audio/wav' })
    await uploadAudioFromBrowserFile(wavFile, durationMs)
  }

  const startRecording = () => {
    if (isUploading.value) {
      return
    }

    if (!recorderManager && canUseBrowserRecorder()) {
      void startBrowserRecording()
      return
    }

    try {
      recorderManager?.start({
        duration: 60000,
        sampleRate: 16000,
        numberOfChannels: 1,
        encodeBitRate: 96000,
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : '当前环境暂不支持录音，请改用选择音频。'
      options.onStatusChange('无法开始录音', message)
    }
  }

  const stopRecording = () => {
    if (!isRecording.value) {
      return
    }

    if (!recorderManager && canUseBrowserRecorder()) {
      void stopBrowserRecording()
      return
    }

    recorderManager?.stop()
  }

  const finalizeUpload = (
    response: Awaited<ReturnType<typeof uploadMediaByFilePath>>,
    durationMs: number,
  ) => {
    if (!response.success) {
      throw new Error(response.message || '上传失败')
    }

    const absoluteAudioUrl = options.toAbsoluteUrl(response.data.url)
    lastAudioUrl.value = absoluteAudioUrl
    lastUploadedFileId.value = response.data.fileId
    lastTranscript.value = ''
    lastAudioDurationMs.value = durationMs
    options.onStatusChange('上传完成', '正在自动识别语音内容。')
    void transcribeLastAudio()
  }

  const uploadAudio = async (filePath: string, durationMs = 0, fileName = 'recording.wav') => {
    isUploading.value = true
    options.onStatusChange('上传中', '正在把音频文件发送到后端。')

    try {
      const response = await uploadMediaByFilePath(options.apiBaseUrl, filePath, fileName)
      finalizeUpload(response, durationMs)
    } catch (error) {
      const message = error instanceof Error ? error.message : '请检查后端服务和网络连接。'
      options.onStatusChange('上传失败', message)
    } finally {
      isUploading.value = false
    }
  }

  const uploadAudioFromBrowserFile = async (file: File, durationMs = 0) => {
    isUploading.value = true
    options.onStatusChange('上传中', '正在把选择的音频文件发送到后端。')

    try {
      const response = await uploadMediaByBrowserFile(options.apiBaseUrl, file)
      finalizeUpload(response, durationMs)
    } catch (error) {
      const message = error instanceof Error ? error.message : '请检查后端服务和网络连接。'
      options.onStatusChange('上传失败', message)
    } finally {
      isUploading.value = false
    }
  }

  const chooseH5AudioFile = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'audio/*'
    input.capture = 'microphone'
    input.onchange = async () => {
      const selectedFile = input.files?.[0]
      if (!selectedFile) {
        return
      }
      await uploadAudioFromBrowserFile(selectedFile)
    }
    input.click()
  }

  const pickAudioFile = () => {
    // #ifdef H5
    chooseH5AudioFile()
    // #endif

    // #ifndef H5
    options.onStatusChange('请使用录音', '当前平台优先验证录音上传链路。')
    // #endif
  }

  const transcribeLastAudio = async () => {
    if (!lastUploadedFileId.value) {
      options.onStatusChange('没有可识别音频', '先录一段或选择一个音频文件上传。')
      return
    }

    options.onStatusChange('识别中', '正在调用 ASR 服务生成转写结果。')

    try {
      const payload = await transcribeMedia(options.apiBaseUrl, lastUploadedFileId.value)
      if (!payload.success) {
        throw new Error(payload.message || '转写失败')
      }

      lastTranscript.value = payload.data.transcript
      options.onTranscriptReady?.(payload.data.transcript)
      options.onStatusChange('识别完成', '转写已自动填入发送框，可手动修改后再发送。')
    } catch (error) {
      const message = error instanceof Error ? error.message : '请检查后端服务和网络连接。'
      options.onStatusChange('识别失败', message)
    }
  }

  const resetMediaState = () => {
    lastAudioUrl.value = ''
    lastUploadedFileId.value = ''
    lastTranscript.value = ''
    lastAudioDurationMs.value = 0
  }

  return {
    isRecording,
    isUploading,
    lastAudioUrl,
    lastUploadedFileId,
    lastTranscript,
    lastAudioDurationMs,
    latestAudioLabel,
    startRecording,
    stopRecording,
    pickAudioFile,
    transcribeLastAudio,
    resetMediaState,
  }
}
