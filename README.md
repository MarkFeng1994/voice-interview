# Voice Interview Workspace

`Voice Interview` 是一个面向移动端的 AI 模拟面试项目，当前仓库包含三端：

- `voice-interview-mobile`
  - 手机端客户端
  - 技术栈：`uni-app + Vue 3 + TypeScript + Pinia`
- `voice-interview-admin`
  - 管理端 Web
  - 技术栈：`Vue 3 + Vite + TypeScript + Ant Design Vue`
- `voice-interview-backend`
  - 后端服务
  - 技术栈：`Spring Boot 3.5.7 + Java 21 + Spring AI 1.0.5`

## 当前状态

所有里程碑已完成：

| 里程碑 | 目标 | 状态 |
|--------|------|------|
| M0 | 方向验证 / 技术 Spike | ✅ |
| M1 | MVP 核心链路（半双工语音面试） | ✅ |
| M2 | 稳定性与体验补强（追问、报告解释、历史回填） | ✅ |
| M3 | 管理端 Web（分类、题库、导入、报表） | ✅ |
| M4 | 全双工升级（DashScope Realtime 代理模式） | ✅ |

具备完整闭环：登录 → 面试配置 → 半双工/全双工面试 → 历史记录 → 面试报告 → 个人中心。

## 环境要求

- Node.js: `>= 24`
- npm: `>= 11`
- Java: `21`
- Maven: `3.9+`

## 快速启动

先准备本地运行配置：

```powershell
Copy-Item .\ops\runtime.local.example.ps1 .\ops\runtime.local.ps1
```

然后按 [RUNBOOK](E:/developSoft/ideaworkspace/voice-interview/ops/RUNBOOK.md) 填写本地环境变量。

### 启动 Mobile

```powershell
cd .\voice-interview-mobile
npm install --legacy-peer-deps
npm run dev:h5
```

常用命令：

- `npm run build:h5`
- `npm run dev:mp-weixin`
- `npm run build:mp-weixin`

### 启动 Admin

```powershell
cd .\voice-interview-admin
npm install
npm run dev
```

### 启动 Backend

```powershell
cd .\voice-interview-backend
mvn spring-boot:run
```

如果启用本地 JDBC + Spring AI：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev,openai'
mvn spring-boot:run
```

一键脚本：

```powershell
.\ops\run-backend-dev.ps1
.\ops\run-mobile-h5.ps1
```

## 文档导航

建议按下面顺序阅读：

- 项目入口
  - [PLAN.md](E:/developSoft/ideaworkspace/voice-interview/PLAN.md)
  - [TASKS.md](E:/developSoft/ideaworkspace/voice-interview/TASKS.md)
- 架构与产品说明
  - [项目架构技术明细.md](E:/developSoft/ideaworkspace/voice-interview/docs/architecture/项目架构技术明细.md)
  - [项目架构技术明细.pdf](E:/developSoft/ideaworkspace/voice-interview/docs/architecture/项目架构技术明细.pdf)
  - [项目架构与功能说明书.md](E:/developSoft/ideaworkspace/voice-interview/docs/architecture/项目架构与功能说明书.md)
- 运行与发布
  - [RUNBOOK](E:/developSoft/ideaworkspace/voice-interview/ops/RUNBOOK.md)
  - [UAT_STEP_BY_STEP](E:/developSoft/ideaworkspace/voice-interview/ops/UAT_STEP_BY_STEP.md)
  - [RELEASE_CHECKLIST](E:/developSoft/ideaworkspace/voice-interview/ops/RELEASE_CHECKLIST.md)
  - [PRE_RELEASE_SIGNOFF](E:/developSoft/ideaworkspace/voice-interview/ops/PRE_RELEASE_SIGNOFF.md)
- 过程归档
  - [docs/archive/superpowers](E:/developSoft/ideaworkspace/voice-interview/docs/archive/superpowers)

## Provider 布局

- `springai`
  - 当前默认 LLM 编排层（Spring AI + ChatClient + 结构化输出）
  - OpenAI 兼容代理，配置 `spring.ai.openai.*` 属性
- `mock`
  - 本地稳定测试
- `ASR/TTS`
  - 独立 provider 配置，不与 LLM provider 强绑定
  - DashScope: `qwen3-asr-flash-realtime` / `qwen3-tts-flash-realtime`
- `Realtime`
  - DashScope `qwen-omni-turbo-realtime` 全双工代理
  - 浏览器 WS ↔ Spring Boot WS ↔ DashScope WS 桥接

## 全双工语音架构

```
浏览器 (AudioWorklet PCM 16kHz)
  ↕ WebSocket /ws/realtime
Spring Boot (RealtimeProxyWebSocketHandler)
  ↕ DashScope WebSocket
qwen-omni-turbo-realtime (PCM 24kHz)
```

关键组件：
- `audio-processor.js` — AudioWorklet PCM 采集
- `useRealtimeInterview.ts` — 全双工 composable
- `RealtimeControls.vue` — 模式切换 + 实时转写 + 打断
- `RealtimeProxyWebSocketHandler` — 音频桥接 + 指标采集
- `RealtimeInterviewStateMachine` — 事件驱动状态机
- `RealtimeFallbackService` — 可用性探测 + 半双工降级

## 说明

- 根目录只保留项目入口文档
- 过程性设计与实施记录已归档到 `docs/archive`
- 运行日志和临时产物不再作为仓库主视图的一部分
