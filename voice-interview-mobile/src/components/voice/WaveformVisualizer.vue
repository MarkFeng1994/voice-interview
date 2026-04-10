<template>
  <view class="waveform-wrap">
    <canvas
      canvas-id="waveformCanvas"
      id="waveformCanvas"
      class="waveform-canvas"
      :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
    />
  </view>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  active: boolean
  amplitudes: number[]
}>()

const canvasWidth = ref(300)
const canvasHeight = ref(60)

let ctx: UniApp.CanvasContext | null = null
let drawId = 0

const BAR_GAP = 2
const MIN_BAR_HEIGHT = 2
const BAR_RADIUS = 1.5

const draw = () => {
  if (!ctx) {
    return
  }

  const w = canvasWidth.value
  const h = canvasHeight.value
  const bars = props.amplitudes
  const barCount = bars.length
  const barWidth = Math.max(1, (w - (barCount - 1) * BAR_GAP) / barCount)
  const centerY = h / 2

  ctx.clearRect(0, 0, w, h)

  for (let i = 0; i < barCount; i += 1) {
    const amplitude = bars[i] || 0.02
    const barHeight = Math.max(MIN_BAR_HEIGHT, amplitude * h * 0.9)
    const x = i * (barWidth + BAR_GAP)
    const y = centerY - barHeight / 2

    // Gradient-like coloring: stronger amplitude = more opaque
    const alpha = props.active
      ? Math.min(1, 0.4 + amplitude * 1.2)
      : 0.25

    if (props.active && amplitude > 0.1) {
      ctx.setFillStyle(`rgba(245, 171, 76, ${alpha})`)
    } else {
      ctx.setFillStyle(`rgba(245, 171, 76, ${alpha * 0.6})`)
    }

    // Draw rounded bar via fillRect (uni-app canvas doesn't support roundRect)
    const r = Math.min(BAR_RADIUS, barWidth / 2, barHeight / 2)
    drawRoundedRect(ctx, x, y, barWidth, barHeight, r)
  }

  ctx.draw()
}

const drawRoundedRect = (
  c: UniApp.CanvasContext,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) => {
  c.beginPath()
  c.moveTo(x + r, y)
  c.lineTo(x + w - r, y)
  c.arcTo(x + w, y, x + w, y + r, r)
  c.lineTo(x + w, y + h - r)
  c.arcTo(x + w, y + h, x + w - r, y + h, r)
  c.lineTo(x + r, y + h)
  c.arcTo(x, y + h, x, y + h - r, r)
  c.lineTo(x, y + r)
  c.arcTo(x, y, x + r, y, r)
  c.closePath()
  c.fill()
}

const startLoop = () => {
  const loop = () => {
    draw()
    drawId = requestAnimationFrame(loop)
  }
  drawId = requestAnimationFrame(loop)
}

const stopLoop = () => {
  if (drawId) {
    cancelAnimationFrame(drawId)
    drawId = 0
  }
}

watch(() => props.active, (val) => {
  if (val) {
    startLoop()
  } else {
    stopLoop()
    // Draw one final frame (idle state)
    setTimeout(() => draw(), 50)
  }
})

onMounted(() => {
  // Get container width via query
  const query = uni.createSelectorQuery().in(getCurrentInstance())
  query.select('.waveform-wrap').boundingClientRect((rect) => {
    if (rect) {
      canvasWidth.value = (rect as UniApp.NodeInfo).width || 300
      canvasHeight.value = 60
    }
  }).exec()

  ctx = uni.createCanvasContext('waveformCanvas', getCurrentInstance())

  // Initial idle draw after a short delay for canvas to be ready
  setTimeout(() => {
    draw()
    if (props.active) {
      startLoop()
    }
  }, 100)
})

onUnmounted(() => {
  stopLoop()
  ctx = null
})
</script>

<script lang="ts">
import { getCurrentInstance } from 'vue'
</script>

<style scoped>
.waveform-wrap {
  width: 100%;
  height: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.waveform-canvas {
  display: block;
}
</style>
