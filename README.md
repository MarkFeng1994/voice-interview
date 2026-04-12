# Voice Interview Workspace

`Voice Interview` 是一个面向移动端的 AI 模拟面试项目，当前仓库包含三端：

- `voice-interview-mobile`
  - 手机端客户端
  - 技术栈：`uni-app + Vue 3 + TypeScript + Pinia`
- `voice-interview-admin`
  - 管理端 Web
  - 技术栈：`Vue 3 + Vite + TypeScript`
- `voice-interview-backend`
  - 后端服务
  - 技术栈：`Spring Boot 3 + Java 21`

## 当前状态

当前仓库已经具备最小可运行闭环：

- 登录 / 注册
- 首页工作台
- 面试配置
- 实时面试会话
- 历史记录
- 面试报告
- 个人中心

推荐把它理解为：`M1 核心链路已打通，正在补 M2 稳定性与体验`。

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

如果启用本地 JDBC + OpenAI 兼容 Provider：

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

- `langchain4j`
  - 当前默认 LLM 编排层
  - 便于后续扩展 `tools / memory / RAG / agent`
- `openai`
  - legacy direct HTTP fallback
- `mock`
  - 本地稳定测试
- `ASR/TTS`
  - 独立 provider 配置，不与 LLM provider 强绑定

当前仓库内推荐的语音方案：

- `ASR -> qwen3-asr-flash-realtime`
- `TTS -> qwen3-tts-flash-realtime`

## 说明

- 根目录只保留项目入口文档
- 过程性设计与实施记录已归档到 `docs/archive`
- 运行日志和临时产物不再作为仓库主视图的一部分
