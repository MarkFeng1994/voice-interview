import { onMounted, onUnmounted } from 'vue'

interface UseAudioPlaybackOptions {
  onPlay?: () => void
  onEnded?: () => void
  onError?: (message: string) => void
}

export const useAudioPlayback = (options: UseAudioPlaybackOptions) => {
  const audioPlayer = uni.createInnerAudioContext()

  onMounted(() => {
    audioPlayer.onPlay(() => {
      options.onPlay?.()
    })

    audioPlayer.onEnded(() => {
      options.onEnded?.()
    })

    audioPlayer.onError((error) => {
      const message = typeof error.errMsg === 'string' ? error.errMsg : '请检查音频链接是否可访问。'
      options.onError?.(message)
    })
  })

  onUnmounted(() => {
    audioPlayer.destroy()
  })

  const playAudio = (audioUrl: string) => {
    audioPlayer.src = audioUrl
    audioPlayer.play()
  }

  const stopAudio = () => {
    audioPlayer.stop()
  }

  return {
    playAudio,
    stopAudio,
  }
}
