import { ref } from 'vue'

const BAR_COUNT = 64
const MIN_AMPLITUDE = 0.02

export const useWaveform = () => {
  const amplitudes = ref<number[]>(new Array(BAR_COUNT).fill(MIN_AMPLITUDE))
  const isActive = ref(false)

  let animationId = 0
  let pendingAmplitude = MIN_AMPLITUDE
  let breathPhase = 0

  const pushSamples = (samples: Float32Array) => {
    let sum = 0
    for (let i = 0; i < samples.length; i += 1) {
      sum += samples[i] * samples[i]
    }
    const rms = Math.sqrt(sum / samples.length)
    pendingAmplitude = Math.max(MIN_AMPLITUDE, Math.min(1, rms * 4))
  }

  const tick = () => {
    if (!isActive.value) {
      return
    }

    const arr = amplitudes.value.slice()
    arr.shift()

    if (pendingAmplitude > MIN_AMPLITUDE * 1.5) {
      arr.push(pendingAmplitude)
    } else {
      breathPhase += 0.06
      const breath = MIN_AMPLITUDE + Math.sin(breathPhase) * 0.008
      arr.push(Math.max(MIN_AMPLITUDE, breath))
    }

    amplitudes.value = arr
    pendingAmplitude = MIN_AMPLITUDE

    animationId = requestAnimationFrame(tick)
  }

  const start = () => {
    if (isActive.value) {
      return
    }
    isActive.value = true
    breathPhase = 0
    pendingAmplitude = MIN_AMPLITUDE
    animationId = requestAnimationFrame(tick)
  }

  const stop = () => {
    isActive.value = false
    if (animationId) {
      cancelAnimationFrame(animationId)
      animationId = 0
    }
  }

  const reset = () => {
    stop()
    amplitudes.value = new Array(BAR_COUNT).fill(MIN_AMPLITUDE)
    pendingAmplitude = MIN_AMPLITUDE
    breathPhase = 0
  }

  return {
    amplitudes,
    isActive,
    pushSamples,
    start,
    stop,
    reset,
  }
}
