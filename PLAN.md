# Voice Interview - 手机端对话式模拟面试方案

## Context

目标不是桌面网页工具，而是**手机上可用、像豆包一样以对话形式进行模拟面试**。

这意味着方案重点要从：

- 浏览器本地 STT
- 桌面后台式页面
- 单纯 Web 页面交互

切换为：

- **移动端优先**
- **语音对话优先**
- **服务端语音链路优先**
- **MVP 先做半双工，后续升级全双工**

---

## 一、产品目标与边界

### 1.1 目标体验

用户在手机上打开应用后，可以：

1. 选择岗位 / 题库 / 面试模式
2. 像和 AI 面试官聊天一样开始面试
3. 听 AI 提问
4. 用语音回答，也可以切换成文本回答
5. 获得 AI 追问、点评和最终报告
6. 回看完整对话和音频/文字记录

### 1.2 需要明确的交互层级

“像豆包一样对话”分成两层：

- **MVP 半双工**：用户按住说话或点击开始录音，松开后上传音频，AI 识别并回复
- **Phase 2 全双工**：像语音通话一样实时说话、实时打断、低延迟连续对话

### 1.3 结论

如果目标是“手机能用、对话式模拟面试”，**MVP 用半双工更稳妥**。

如果目标是“像豆包通话模式一样可随时打断、像电话一样自然”，那就不能继续依赖浏览器 Web Speech API，必须走：

- 手机端音频采集
- 云端 ASR / TTS
- WebSocket 或 RTC
- 服务端状态机

---

## 二、总体方案判断

当前方案里最需要改的不是 Vue，而是**移动端语音链路**。

### 2.1 原方案中不适合手机优先的点

- `Web Speech API` 不适合作为移动端主 STT 方案
- `Ant Design Vue + 后台页面结构` 更偏桌面管理台，不像手机聊天产品
- 单一 WebSocket 文本协议可以做 MVP，但不够支撑“像豆包一样”的实时语音通话体验
- 题库管理和面试对话是两种完全不同的前端形态，不建议强行放在同一个移动端体验里

### 2.2 更合理的方向

拆成两个前端：

- **移动端客户端**：负责模拟面试、历史、报告、个人中心
- **管理端 Web**：负责题库管理、分类管理、导入、运营配置

两端都使用 Vue 体系：

- 移动端：`uni-app(Vue 3 + TypeScript + Pinia)`
- 管理端：`Vue 3 + Vite + Ant Design Vue + Pinia`

这样既满足“前端用 Vue”，又满足“手机端对话体验优先”。

---

## 三、技术选型

### 3.1 移动端客户端

| 层 | 技术 | 理由 |
|---|---|---|
| 客户端框架 | uni-app + Vue 3 + TypeScript | Vue 技术栈，适合移动端优先，后续可扩 H5 / App / 小程序 |
| 状态管理 | Pinia | Vue 官方推荐 |
| UI | uni-ui + 自定义聊天组件 | 手机端需要聊天式界面，通用后台组件库不合适 |
| 音频采集 | uni-app 录音能力 / 原生插件 | 比浏览器 Web Speech API 更可控 |
| 音频播放 | 原生音频播放器能力 | 更适合手机端连续播放 |

### 3.2 管理端 Web

| 层 | 技术 | 理由 |
|---|---|---|
| 前端框架 | Vue 3 + TypeScript + Vite | 适合题库管理和后台页面 |
| UI | Ant Design Vue | 管理后台效率高 |
| 状态管理 | Pinia | 与移动端保持一致 |

### 3.3 后端与基础设施

| 层 | 技术 | 理由 |
|---|---|---|
| 后端 | Java 17+ / Spring Boot 3.x | 熟悉、稳定 |
| ORM | MyBatis-Plus | 降低 CRUD 成本 |
| 数据库 | MySQL 8.0 | 主存储 |
| 缓存 | Redis 7.x | 会话状态、幂等、限流、重连恢复 |
| 对象存储 | 本地磁盘（MVP）/ MinIO（后续） | 存放用户录音和 TTS 音频 |
| 实时通信 | WebSocket（MVP） -> RTC（Phase 2） | 先半双工，后全双工 |
| AI | OpenAI / Claude / 豆包兼容模型接口 | 面试逻辑和报告生成 |
| ASR | 云端语音识别 | 手机端统一方案，不依赖浏览器兼容性 |
| TTS | Edge TTS（MVP） -> 豆包 / 火山 / 阿里云（后续） | MVP 成本低，后续可升级中文体验 |

### 3.4 语音路线

#### MVP

- 用户按住录音
- 手机端生成音频文件
- 上传后端
- 后端调用 ASR
- 后端调用 LLM
- 后端调用 TTS
- 移动端播放 AI 音频

这是**聊天式语音问答**，不是**实时语音通话**。

#### Phase 2

- 接入 RTC / 实时流式 ASR / 流式 TTS
- 支持边说边识别
- 支持 AI 播放中被打断
- 支持更像豆包的通话模式

---

## 四、为什么不再以 Web Speech API 为核心

当前时间是 **2026-03-20**。我核对了官方资料后，这个结论比较明确：

- MDN 当前仍把 `SpeechRecognition` 标记为 **Limited availability**
- MDN 明确说明，一些浏览器中识别是 **server-based recognition**
- 这意味着它既不稳定，也不适合作为手机主方案

因此本项目如果要求“手机可用”，应该把：

- **STT 从前端移到服务端/云端**
- **前端负责录音和播放**

而不是继续围绕浏览器本地识别设计。

---

## 五、系统架构

### 5.1 MVP 架构（手机端半双工）

```
手机端客户端 (uni-app)
  ├─ 录音
  ├─ 音频播放
  ├─ 聊天式对话界面
  ├─ REST API（登录/题库选择/历史/报告）
  └─ WebSocket（面试状态同步）
        │
    Nginx / API Gateway
        │
  Spring Boot 后端
  ├─ 用户模块
  ├─ 题库模块
  ├─ 面试模块
  │   ├─ 会话管理
  │   ├─ 服务端状态机
  │   ├─ 录音消息处理
  │   ├─ 追问编排
  │   └─ 报告生成
  ├─ AI 模块
  ├─ ASR 模块
  ├─ TTS 模块
  └─ 媒体文件模块
        │
  MySQL + Redis + Object Storage
```

### 5.2 Phase 2 架构（全双工）

```
手机端客户端 (uni-app App / 小程序 / H5)
  ├─ RTC SDK
  ├─ 实时收音 / 播放
  ├─ 聊天式对话界面
  └─ REST API
        │
      RTC
        │
  AI 实时语音编排层
  ├─ 流式 ASR
  ├─ LLM
  ├─ 流式 TTS
  └─ 打断 / VAD / 状态同步
```

### 5.3 核心原则

- **服务端状态机是唯一权威**
- **模型负责建议，后端负责裁决**
- **MVP 先保证可用，再追求像电话一样自然**
- **手机端优先做对话体验，不优先做复杂后台**

---

## 六、前端划分与目录结构

## 6.1 移动端客户端

```
voice-interview-mobile/
├── src/
│   ├── pages/
│   │   ├── login/
│   │   │   └── index.vue
│   │   ├── home/
│   │   │   └── index.vue
│   │   ├── interview/
│   │   │   ├── setup.vue
│   │   │   ├── session.vue
│   │   │   └── report.vue
│   │   ├── history/
│   │   │   ├── list.vue
│   │   │   └── detail.vue
│   │   └── profile/
│   │       └── index.vue
│   ├── components/
│   │   ├── chat/
│   │   │   ├── MessageBubble.vue
│   │   │   ├── VoiceMessageCard.vue
│   │   │   └── TypingIndicator.vue
│   │   ├── interview/
│   │   │   ├── RecordButton.vue
│   │   │   ├── AudioPlayerBar.vue
│   │   │   ├── InterviewHeader.vue
│   │   │   └── InterviewProgress.vue
│   │   └── common/
│   │       ├── EmptyState.vue
│   │       └── LoadingView.vue
│   ├── stores/
│   │   ├── user.ts
│   │   ├── interview.ts
│   │   └── history.ts
│   ├── composables/
│   │   ├── useRecorder.ts
│   │   ├── useAudioPlayer.ts
│   │   ├── useInterviewSocket.ts
│   │   └── useInterviewSession.ts
│   ├── services/
│   │   ├── http.ts
│   │   ├── authApi.ts
│   │   ├── interviewApi.ts
│   │   └── mediaApi.ts
│   └── types/
│       ├── interview.ts
│       └── user.ts
```

### 6.2 管理端 Web

```
voice-interview-admin/
├── src/
│   ├── views/
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── question-bank/
│   │   ├── categories/
│   │   ├── imports/
│   │   └── reports/
│   ├── components/
│   ├── stores/
│   ├── router/
│   └── services/
```

### 6.3 为什么建议双前端

因为这两个场景完全不同：

- 手机端重点是“像聊天一样面试”
- 管理端重点是“像后台一样维护题库”

如果强行只做一个前端，会导致：

- 手机端体验不像聊天应用
- 题库管理在手机上很难用
- 界面和交互目标冲突

---

## 七、移动端核心交互设计

### 7.1 Session 页面形态

手机端核心页面应该像聊天应用，而不是后台页面。

```
+--------------------------------------------------+
| Java 后端模拟面试                  12:31  [结束] |
+--------------------------------------------------+
| AI：请你先做一个 1 分钟的自我介绍。               |
| [音频播放条]                                      |
|                                                  |
| 我：这是我的语音回答（可播放）                    |
| [语音消息卡片 00:42]                              |
|                                                  |
| AI：你提到做过高并发项目。                        |
|     讲一下你当时怎么做限流和降级？                |
| [音频播放条]                                      |
|                                                  |
| 实时状态：正在思考 / 正在播放 / 等待你回答        |
+--------------------------------------------------+
| [按住说话]    [键盘输入]    [跳过]    [结束面试]  |
+--------------------------------------------------+
```

### 7.2 移动端输入模式

- 默认：`按住说话`
- 兜底：`文本输入`
- 后续：`连续对话模式`

### 7.3 MVP 不建议一开始做的能力

- 实时逐字字幕
- AI 播放时随时插话打断
- 后台复杂配置页面直接塞进手机端
- 多角色同时连麦

---

## 八、后端模块与目录结构

```
voice-interview-backend/
├── pom.xml
├── src/main/java/com/interview/
│   ├── VoiceInterviewApplication.java
│   ├── common/
│   │   ├── config/
│   │   ├── exception/
│   │   ├── result/
│   │   ├── security/
│   │   └── util/
│   ├── module/
│   │   ├── user/
│   │   ├── question/
│   │   ├── interview/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── engine/              # 状态机、追问、切题、报告编排
│   │   │   ├── websocket/
│   │   │   ├── mapper/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── ai/
│   │   ├── asr/
│   │   ├── tts/
│   │   └── media/
│   └── job/
└── src/main/resources/
    ├── application.yml
    ├── prompts/
    └── db/schema.sql
```

---

## 九、数据库设计（9张核心表）

### 9.1 总览

| 表名 | 用途 |
|---|---|
| `t_user` | 用户 |
| `t_category` | 题目分类 |
| `t_question` | 题目 |
| `t_interview_session` | 面试会话主表 |
| `t_interview_question` | 会话题目快照 |
| `t_interview_round` | 每轮问答记录 |
| `t_interview_report` | 结构化报告 |
| `t_media_file` | 用户录音 / AI 音频文件元信息 |
| `t_import_task` | 导入任务 |

### 9.2 新增的关键点

相对之前版本，手机端方案必须补上：

- `t_media_file`：存用户录音和 TTS 音频
- `user_audio_file_id`：每一轮能关联到原始录音
- `asr_text`：保存语音识别后的文本
- `answer_mode`：区分 `VOICE` / `TEXT` / `SKIP`
- `interaction_mode`：区分 `PUSH_TO_TALK` / `FULL_DUPLEX`

### 9.3 核心表结构建议

#### t_interview_session

- `user_id`
- `status`
- `selected_category_ids`
- `config_json`
- `interaction_mode`
- `current_question_index`
- `total_question_count`
- `overall_score`
- `started_at`
- `finished_at`

#### t_interview_question

- `session_id`
- `question_id`
- `question_index`
- `title_snapshot`
- `content_snapshot`
- `answer_snapshot`
- `difficulty_snapshot`

#### t_interview_round

- `session_id`
- `interview_question_id`
- `question_index`
- `follow_up_index`
- `round_type`
- `user_audio_file_id`
- `user_answer_mode`
- `asr_text`
- `final_user_answer_text`
- `ai_message_text`
- `ai_analysis`
- `tts_audio_file_id`
- `score`
- `duration_ms`

#### t_interview_report

- `session_id`
- `overall_score`
- `overall_comment`
- `report_json`
- `report_version`

#### t_media_file

- `user_id`
- `biz_type` (`USER_RECORDING` / `AI_TTS`)
- `storage_type`
- `file_key`
- `mime_type`
- `duration_ms`
- `size_bytes`
- `expire_at`

---

## 十、AI Prompt 与流程控制

### 10.1 核心原则

- 模型不直接控制流程
- 模型输出结构化建议
- 服务端状态机根据：
  - 当前题目
  - 追问次数
  - 题目总数
  - 用户是否跳过
  - 用户回答质量
  来决定下一步

### 10.2 模型输出建议格式

```json
{
  "spokenText": "面向候选人的一句话",
  "decisionSuggestion": "FOLLOW_UP",
  "scoreSuggestion": 82,
  "analysis": "给服务端看的分析",
  "focusTags": ["并发", "限流", "降级"]
}
```

### 10.3 为什么这样设计

因为一旦做手机端语音链路，流程复杂度会变高：

- 有录音上传失败
- 有 ASR 失败
- 有 TTS 失败
- 有网络中断
- 有用户中途切文本

所以必须让**服务端状态机**做最终决定，而不是让 LLM 直接控制前端行为。

---

## 十一、MVP 语音时序

### 11.1 半双工时序

```
用户             手机端客户端            后端                 ASR / LLM / TTS
 |                   |                    |                         |
 | 点击开始面试       |                    |                         |
 |------------------>| POST /interviews   |                         |
 |                   |------------------->|                         |
 |                   |<--- sessionId/wsTicket ----------------------|
 |                   |---- WebSocket connect ----------------------->|
 |                   |<--- AI首题 + audioUrl -----------------------|
 | 听 AI 提问         | 播放音频             |                         |
 |                   |                    |                         |
 | 按住说话           | 本地录音              |                         |
 |------------------>|                    |                         |
 | 松开发送           | 上传音频文件          |                         |
 |                   |---- POST /media/upload --------------------->|
 |                   |<--- fileId ----------------------------------|
 |                   |---- WS: USER_AUDIO(fileId) ----------------->|
 |                   |                    |---- ASR ---------------->|
 |                   |                    |<--- transcript ----------|
 |                   |                    |---- LLM ---------------->|
 |                   |                    |<--- decision ------------|
 |                   |                    |---- TTS ---------------->|
 |                   |                    |<--- aiAudio -------------|
 |                   |<--- AI_MESSAGE + audioUrl -------------------|
 | 听 AI 追问/点评     | 播放音频              |                         |
```

### 11.2 这个 MVP 的特点

- 像聊天，不像通话
- 稳定性高
- 手机端更容易落地
- 成本和复杂度可控

---

## 十二、Phase 2：像豆包一样的“通话模式”

如果要进一步做到更接近豆包，需要明确这是**第二阶段架构升级**，不是在当前 MVP 上靠几行代码补出来。

### 12.1 需要新增的能力

- RTC / WebRTC 实时音频链路
- 流式 ASR
- 流式 TTS
- VAD（语音活动检测）
- 打断处理
- 边播边收
- 更低延迟的 AI 编排

### 12.2 Phase 2 的产品表现

- AI 说话时用户可以插话
- AI 可以自动停下并重新理解用户输入
- 对话更像电话
- 延迟目标压到 1 秒级别

### 12.3 架构判断

如果你明确要“像豆包通话模式”，建议在 Phase 2 优先考虑 **RTC 方案**，而不是继续基于普通 WebSocket 文本消息硬扛。

---

## 十三、核心 API 设计

### 13.1 REST API

| 模块 | Method | Path | 说明 |
|---|---|---|---|
| 认证 | POST | `/api/auth/register` | 注册 |
| 认证 | POST | `/api/auth/login` | 登录 |
| 用户 | GET | `/api/user/profile` | 当前用户 |
| 分类 | GET | `/api/categories` | 分类树 |
| 题目 | GET | `/api/questions` | 题目列表 |
| 面试 | POST | `/api/interviews` | 创建面试会话 |
| 面试 | GET | `/api/interviews/{id}` | 会话详情 |
| 面试 | GET | `/api/interviews/{id}/state` | 当前状态 |
| 面试 | GET | `/api/interviews/{id}/report` | 报告 |
| 面试 | POST | `/api/interviews/{id}/resume-ticket` | 重连票据 |
| 媒体 | POST | `/api/media/upload` | 上传用户录音 |
| 媒体 | GET | `/api/media/{fileId}` | 获取音频 |

### 13.2 WebSocket 协议（MVP）

**连接地址**

`wss://host/ws/interview?sessionId={sessionId}&ticket={wsTicket}`

**客户端 -> 服务端**

```json
{ "type": "USER_AUDIO", "messageId": "uuid", "seq": 3, "fileId": "f_123", "durationMs": 42000 }
{ "type": "USER_TEXT", "messageId": "uuid", "seq": 4, "content": "这是我的文本回答" }
{ "type": "SKIP_QUESTION", "messageId": "uuid", "seq": 5 }
{ "type": "END_INTERVIEW", "messageId": "uuid", "seq": 6 }
```

**服务端 -> 客户端**

```json
{ "type": "SESSION_READY", "sessionId": 123, "status": "IN_PROGRESS" }
{ "type": "ANSWER_ACK", "messageId": "uuid", "seq": 3 }
{ "type": "TRANSCRIPT_READY", "seq": 3, "text": "识别后的文本" }
{ "type": "AI_MESSAGE", "questionIndex": 1, "followUpIndex": 1, "content": "你的回答提到了限流。那你说说令牌桶和漏桶的区别。", "audioUrl": "/api/media/a_456" }
{ "type": "STATUS_CHANGE", "status": "COMPLETED", "reportUrl": "/api/interviews/123/report" }
{ "type": "ERROR", "code": "ASR_FAILED", "message": "语音识别失败，请重试或改用文本回答" }
```

### 13.3 鉴权与幂等

- 会话创建时返回短期 `wsTicket`
- `wsTicket` 绑定 `sessionId + userId`
- 每条消息带 `messageId + seq`
- Redis 记录最近处理的 `seq`
- 避免重复发送导致重复追问或重复入库

---

## 十四、实施计划

### Phase 0：方向验证

目标：验证“手机端语音对话”是否跑得通

| 步骤 | 任务 |
|---|---|
| 1 | 用 uni-app 做一个手机聊天式页面原型 |
| 2 | 打通本地录音 -> 上传后端 -> 获取回放 |
| 3 | 打通音频文件 -> ASR -> 返回文本 |
| 4 | 打通 LLM -> TTS -> 返回 AI 音频 |
| 5 | 完成 1 道题完整问答闭环 |

### Phase 1：MVP（手机端半双工）

目标：让用户真的可以在手机上做对话式模拟面试

| 步骤 | 任务 |
|---|---|
| 1 | 后端骨架 + 鉴权 |
| 2 | 题库与分类 |
| 3 | 会话状态机 |
| 4 | 媒体文件上传与管理 |
| 5 | ASR 接入 |
| 6 | LLM 面试逻辑 |
| 7 | TTS 接入 |
| 8 | 移动端聊天式面试页 |
| 9 | 报告生成与历史回看 |
| 10 | 文本回答兜底 |

### Phase 2：通话模式升级

目标：更接近豆包实时语音对话

| 步骤 | 任务 |
|---|---|
| 1 | 接入 RTC |
| 2 | 流式 ASR |
| 3 | 流式 TTS |
| 4 | 打断与 VAD |
| 5 | 全双工会话管理 |
| 6 | 更低延迟编排 |

### Phase 3：管理端与完善能力

| 任务 | 说明 |
|---|---|
| 管理端 Web | 分类、题目、导入、历史、报表 |
| 文件导入 | Txt / Json / Excel |
| 爬取导入 | 白名单 + 限流 + 内容清洗 |
| 音色选择 | 不同面试官声音 |
| 多面试风格 | 温和 / 压力 / 深挖 |
| 题目推荐 | 基于历史表现推荐薄弱项 |

---

## 十五、验证方式

1. **手机真机验证**
   - Android 真机
   - iPhone 真机
2. **语音链路验证**
   - 录音成功
   - 上传成功
   - ASR 成功
   - TTS 成功
   - 播放成功
3. **失败场景验证**
   - 识别失败
   - 网络中断
   - WS 重连
   - 音频上传失败
   - 改用文本回答
4. **面试流程验证**
   - 正常问答
   - 追问
   - 跳题
   - 中途结束
   - 查看报告

---

## 十六、最终建议

如果你的真实目标是：

**“手机上能像豆包一样对话式模拟面试”**

那方案应该这样定：

### 必须调整

- 前端主客户端改成 **移动端优先 Vue 方案**，推荐 `uni-app(Vue 3)`
- 不再以 `Web Speech API` 作为核心 STT 方案
- 题库管理和手机面试分开做
- MVP 先做 **半双工聊天式语音面试**
- 想做到“像豆包电话模式”，放到 **Phase 2 RTC 升级**

### 这样做的好处

- 能更快做出真正可用的手机版本
- 不会被浏览器语音兼容性卡死
- 产品体验和技术实现更一致
- 后续可以平滑升级到更自然的实时语音交互

### 一句话结论

你的方向应该从“桌面 Web 语音面试工具”改成：

**“Vue 体系下的移动端优先 AI 面试产品，MVP 用半双工语音对话，后续升级 RTC 全双工”**
