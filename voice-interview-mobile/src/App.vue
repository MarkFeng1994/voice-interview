<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

onLaunch(() => {
  userStore.bootstrap()
});
onShow(() => {
  userStore.bootstrap()
});
onHide(() => {});
</script>
<style>
@import './styles/studio.css';
:root,
page {
  --studio-bg: #09090b;
  --studio-bg-soft: #18181b;
  --studio-panel: rgba(24, 24, 27, 0.92);
  --studio-panel-strong: rgba(14, 14, 16, 0.96);
  --studio-panel-muted: rgba(255, 255, 255, 0.04);
  --studio-line: rgba(129, 140, 248, 0.18);
  --studio-line-strong: rgba(129, 140, 248, 0.4);
  --studio-shadow: 0 1px 3px rgba(0, 0, 0, 0.24);
  --studio-text: #fafafa;
  --studio-text-muted: #a1a1aa;
  --studio-text-soft: #71717a;
  --studio-signal: #818cf8;
  --studio-signal-strong: #6366f1;
  --studio-signal-soft: #c7d2fe;
  --studio-success: #4ade80;
  --studio-danger: #f87171;
  --studio-info: #60a5fa;
  --studio-radius: 12rpx;
  --studio-radius-lg: 16rpx;
  --studio-radius-xl: 16rpx;
  --studio-font-display: "Inter", -apple-system, "PingFang SC", "Hiragino Sans GB", sans-serif;
  --studio-font-body: "Inter", -apple-system, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

page {
  min-height: 100%;
  background: #09090b;
  color: var(--studio-text);
}

view,
text,
button,
input,
textarea {
  font-family: var(--studio-font-body);
  box-sizing: border-box;
}

button {
  transition: opacity 120ms ease;
}

button:active {
  opacity: 0.8;
}

@keyframes studioFloatIn {
  from {
    opacity: 0;
    transform: translateY(12rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes studioPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(129, 140, 248, 0.16);
  }
  50% {
    box-shadow: 0 0 0 10rpx rgba(129, 140, 248, 0);
  }
}
</style>
