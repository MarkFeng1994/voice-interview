<template>
  <view class="realtime-controls">
    <!-- Mode Toggle -->
    <view class="mode-toggle">
      <text class="mode-label">对话模式</text>
      <view class="mode-switch">
        <button
          class="mode-option"
          :class="{ active: !isRealtimeMode }"
          @click="switchToHalfDuplex"
        >
          半双工
        </button>
        <button
          class="mode-option"
          :class="{ active: isRealtimeMode }"
          :disabled="!realtimeAvailable"
          @click="switchToRealtime"
        >
          全双工
        </button>
      </view>
    </view>

    <!-- Realtime Status -->
    <view v-if="isRealtimeMode" class="realtime-status">
      <view class="status-row">
        <text class="status-label">连接状态</text>
        <text class="status-value" :class="connectionStatus">
          {{ connectionStatusLabel }}
        </text>
      </view>
      <view v-if="isAiSpeaking" class="status-row">
        <text class="status-label">AI 正在说话</text>
        <button
          v-if="canInterrupt"
          class="interrupt-btn"
          @click="handleInterrupt"
        >
          打断
        </button>
      </view>
    </view>

    <!-- Live Transcript -->
    <view v-if="isRealtimeMode && showTranscript" class="live-transcript">
      <view class="transcript-section">
        <text class="transcript-label">你说：</text>
        <text class="transcript-text">{{ userTranscript || '...' }}</text>
      </view>
      <view class="transcript-section">
        <text class="transcript-label">AI：</text>
        <text class="transcript-text">{{ assistantTranscript || '...' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  isRealtimeMode: boolean
  realtimeAvailable: boolean
  connectionStatus: 'connected' | 'connecting' | 'disconnected' | 'error'
  isAiSpeaking: boolean
  canInterrupt: boolean
  userTranscript?: string
  assistantTranscript?: string
  showTranscript?: boolean
}

interface Emits {
  (e: 'switchToHalfDuplex'): void
  (e: 'switchToRealtime'): void
  (e: 'interrupt'): void
}

const props = withDefaults(defineProps<Props>(), {
  showTranscript: true
})

const emit = defineEmits<Emits>()

const connectionStatusLabel = computed(() => {
  switch (props.connectionStatus) {
    case 'connected': return '已连接'
    case 'connecting': return '连接中...'
    case 'disconnected': return '已断开'
    case 'error': return '连接失败'
    default: return '未知'
  }
})

const switchToHalfDuplex = () => {
  emit('switchToHalfDuplex')
}

const switchToRealtime = () => {
  emit('switchToRealtime')
}

const handleInterrupt = () => {
  emit('interrupt')
}
</script>

<style scoped>
.realtime-controls {
  padding: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}

.mode-toggle {
  margin-bottom: 24rpx;
}

.mode-label {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 12rpx;
}

.mode-switch {
  display: flex;
  gap: 12rpx;
}

.mode-option {
  flex: 1;
  padding: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 2rpx solid transparent;
  border-radius: 12rpx;
  color: rgba(255, 255, 255, 0.6);
  font-size: 28rpx;
  transition: all 0.2s;
}

.mode-option.active {
  background: rgba(99, 102, 241, 0.2);
  border-color: rgb(99, 102, 241);
  color: rgb(99, 102, 241);
}

.mode-option:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.realtime-status {
  padding: 16rpx;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}

.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.status-row:last-child {
  margin-bottom: 0;
}

.status-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
}

.status-value {
  font-size: 24rpx;
  font-weight: 500;
}

.status-value.connected {
  color: rgb(34, 197, 94);
}

.status-value.connecting {
  color: rgb(251, 191, 36);
}

.status-value.disconnected,
.status-value.error {
  color: rgb(239, 68, 68);
}

.interrupt-btn {
  padding: 8rpx 24rpx;
  background: rgb(239, 68, 68);
  border-radius: 8rpx;
  color: white;
  font-size: 24rpx;
  border: none;
}

.live-transcript {
  padding: 16rpx;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12rpx;
}

.transcript-section {
  margin-bottom: 16rpx;
}

.transcript-section:last-child {
  margin-bottom: 0;
}

.transcript-label {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 8rpx;
}

.transcript-text {
  display: block;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.5;
}
</style>
