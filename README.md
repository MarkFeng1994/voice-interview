# Voice Interview Workspace

当前仓库已完成 `M0-01` 的基础工程初始化，包含三个子项目：

- `voice-interview-mobile`
  - 手机端客户端
  - 技术栈：`uni-app + Vue 3 + TypeScript + Pinia`
- `voice-interview-admin`
  - 管理端 Web
  - 技术栈：`Vue 3 + Vite + TypeScript`
- `voice-interview-backend`
  - 后端服务
  - 技术栈：`Spring Boot 3 + Java 21`

## Environment

- Node.js: `>= 24`
- npm: `>= 11`
- Java: `21`
- Maven: `3.9+`

## Quick Start

推荐先准备本地运行配置：

```powershell
Copy-Item .\ops\runtime.local.example.ps1 .\ops\runtime.local.ps1
```

再按 [ops/RUNBOOK.md](E:/developSoft/ideaworkspace/voice-interview/ops/RUNBOOK.md) 填入本地环境变量。

### Mobile

```powershell
cd .\voice-interview-mobile
npm install --legacy-peer-deps
npm run dev:h5
```

常用命令：

- `npm run build:h5`
- `npm run dev:mp-weixin`
- `npm run build:mp-weixin`
- 真实手机调试时，建议在 `voice-interview-mobile/.env` 中配置 `VITE_API_BASE_URL=http://你的局域网IP:8080`
- 当前 `pinia` 安装在 `npm 11` 下需要 `--legacy-peer-deps`

### Admin

```powershell
cd .\voice-interview-admin
npm install
npm run dev
```

常用命令：

- `npm run build`
- `npm run preview`

### Backend

```powershell
cd .\voice-interview-backend
mvn spring-boot:run
```

常用命令：

- `mvn test`
- `mvn -DskipTests package`
- `src/main/resources/db/schema.sql` 已提供首版 MySQL 表结构基线
- `src/main/resources/application-dev.properties` 已提供本地 MySQL / Redis 配置模板
- `src/main/resources/application-openai.properties` 已提供真实 OpenAI 兼容 Provider 的 profile 模板
- `src/main/resources/application-sherpa.properties` 已提供 sherpa ASR profile 模板
- `src/main/resources/application-dashscope.properties` 已提供 DashScope realtime 语音 profile 模板
- 默认运行仍走 `mock`，并显式关闭 DataSource 自动配置
- 想启用 JDBC 骨架时，使用 `dev` profile：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

切到 OpenAI 兼容 Provider 时，推荐直接叠加 `openai` profile：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev,openai'
mvn spring-boot:run
```

如果三条能力共用同一个兼容站，至少需要这些环境变量：

```powershell
$env:APP_AI_PROVIDER='openai'
$env:APP_ASR_PROVIDER='openai'
$env:APP_TTS_PROVIDER='openai'
$env:APP_OPENAI_API_KEY='你的密钥'
```

可选项：

```powershell
$env:APP_OPENAI_BASE_URL='https://api.openai.com/v1'
$env:APP_OPENAI_CHAT_MODEL='gpt-4o-mini'
$env:APP_OPENAI_TRANSCRIPTION_MODEL='gpt-4o-mini-transcribe'
$env:APP_OPENAI_SPEECH_MODEL='gpt-4o-mini-tts'
$env:APP_OPENAI_VOICE='alloy'
```

如果需要混合配置，例如 `LLM` 走第三方中转站、`ASR/TTS` 继续走 mock 或后续切别家，可直接用分能力环境变量：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev,openai'
$env:APP_AI_PROVIDER='openai'
$env:APP_ASR_PROVIDER='mock'
$env:APP_TTS_PROVIDER='mock'
$env:APP_OPENAI_AI_BASE_URL='https://your-llm-proxy/v1'
$env:APP_OPENAI_AI_API_KEY='你的 LLM key'
$env:APP_OPENAI_AI_MODEL='gpt-5.4-mini'
```

如果后续要把语音能力切到别的兼容服务，也可以单独配置：

```powershell
$env:APP_ASR_PROVIDER='openai'
$env:APP_TTS_PROVIDER='openai'
$env:APP_OPENAI_ASR_BASE_URL='https://your-asr-provider/v1'
$env:APP_OPENAI_ASR_API_KEY='你的 ASR key'
$env:APP_OPENAI_ASR_MODEL='gpt-4o-mini-transcribe'
$env:APP_OPENAI_TTS_BASE_URL='https://your-tts-provider/v1'
$env:APP_OPENAI_TTS_API_KEY='你的 TTS key'
$env:APP_OPENAI_TTS_MODEL='gpt-4o-mini-tts'
$env:APP_OPENAI_TTS_VOICE='alloy'
```

如果语音能力切到阿里云百炼 realtime 模型，推荐叠加 `dashscope` profile：

```powershell
$env:SPRING_PROFILES_ACTIVE='dev,openai,dashscope'
$env:APP_AI_PROVIDER='openai'
$env:APP_ASR_PROVIDER='dashscope'
$env:APP_TTS_PROVIDER='dashscope'
$env:APP_OPENAI_AI_BASE_URL='https://你的 LLM 中转站/v1'
$env:APP_OPENAI_AI_API_KEY='你的 LLM key'
$env:APP_OPENAI_AI_MODEL='gpt-5.4-mini'
$env:APP_DASHSCOPE_BASE_URL='wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
$env:APP_DASHSCOPE_API_KEY='你的百炼 key'
$env:APP_DASHSCOPE_ASR_MODEL='qwen3-asr-flash-realtime'
$env:APP_DASHSCOPE_ASR_LANGUAGE='zh'
$env:APP_DASHSCOPE_TTS_MODEL='qwen3-tts-flash-realtime'
$env:APP_DASHSCOPE_TTS_VOICE='Cherry'
```

如果你仍保留腾讯云上的 `sherpa-onnx-service` 作为备用 ASR，部署资产位于：

- `ops/sherpa-onnx-service/README.md`
- `ops/sherpa-onnx-service/app.py`

它暴露的接口是：

- `GET /healthz`
- `POST /asr`

当前仓库内推荐的语音方案已调整为：

- `ASR -> qwen3-asr-flash-realtime`
- `TTS -> qwen3-tts-flash-realtime`

切到 Redis 会话存储时：

```powershell
$env:APP_INTERVIEW_SESSION_STORE='redis'
$env:SPRING_DATA_REDIS_HOST='127.0.0.1'
$env:SPRING_DATA_REDIS_PORT='6379'
```

默认仍使用内存会话存储：

```powershell
$env:APP_INTERVIEW_SESSION_STORE='memory'
```

一键启动脚本：

```powershell
.\ops\run-backend-dev.ps1
.\ops\run-mobile-h5.ps1
```

## Verified In This Round

以下命令已在本地执行通过：

- `voice-interview-mobile`: `npm run build:h5`
- `voice-interview-admin`: `npm run build`
- `voice-interview-backend`: `mvn -DskipTests package`
- `voice-interview-backend`: `dev + jdbc + jwt` 实测可跑登录、资料、会话、媒体、报告链路
- `voice-interview-backend`: JDBC 模式下最终报告会落到 `t_interview_report`
- `voice-interview-backend`: `GET /api/system/providers` 可查看 AI / ASR / TTS 当前 provider 运行态
- `voice-interview-backend`: `/api/resumes/upload` / `/{resumeId}` / `/{resumeId}/reparse` 已接入简历上传与解析状态闭环
- `voice-interview-backend`: `POST /api/system/media/cleanup` 可手动清理已过期媒体
- `voice-interview-backend`: `t_media_file` 已接入上传音频和生成音频元数据记录
- `voice-interview-backend`: 分类 / 题目 CRUD 已在 MySQL 实测通过
- `voice-interview-backend`: `wsTicket + /ws/interview` 已完成，移动端会话支持实时同步
- `voice-interview-backend`: 面试会话已透出 `stage / durationMinutes / maxFollowUpPerQuestion`
- `voice-interview-backend`: `X-Request-Id` 与请求开始/结束日志已接入
- `voice-interview-admin`: 已具备最小可用的登录、分类管理、题目管理界面
- `voice-interview-backend` / `voice-interview-admin`: 文本导入题目能力已实测通过
- `ops/smoke-check.ps1`: 已可执行主链路 smoke check
- `ops/voice-interview-backend.service` / `ops/nginx.voice-interview.conf.example`: 已提供部署模板
- `ops/MOBILE_UAT_MATRIX.md` / `ops/PRE_RELEASE_SIGNOFF.md`: 已提供真机联调矩阵和发布前签字清单

## Next Step

按 [TASKS.md](E:/developSoft/ideaworkspace/voice-interview/TASKS.md) 的顺序，下一步建议进入：

- 真机联调矩阵与发布前检查
- 更细的观测与追踪
- 后台报表增强
