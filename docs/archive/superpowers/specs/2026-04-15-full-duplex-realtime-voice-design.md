# 全双工实时语音面试 - 设计文档

> **日期**: 2026-04-15
> **状态**: Implemented
> **方案**: DashScope Qwen-Omni-Realtime API 后端代理模式

---

## 1. 概述

### 1.1 目标

将现有半双工（按住说话）面试交互升级为全双工实时语音对话，实现:

- **首字节延迟 < 1s**（现有 5-15s）
- **自然对话**：用户随时说、随时听，无需按住按钮
- **打断支持**：用户可打断 AI 回复，切换话题
- **自动 VAD**：服务端语音活动检测，自动判定用户说话结束
- **无缝降级**：全双工不可用时自动切换回半双工，面试进度不丢失

### 1.2 效果对比

**半双工（现有）- 对讲机模式:**

```
你: [按住按钮] "Spring Boot 的自动配置原理是什么？" [松开]
   → 等待 5-10 秒 (上传 → ASR → LLM → TTS → 下载 → 播放)
AI: [开始播放] "Spring Boot 的自动配置主要依赖..."
   → 必须等 AI 说完才能继续
你: [再次按住] "那 @Conditional 注解是怎么工作的？"
```

**全双工（升级后）- 面对面模式:**

```
你: "Spring Boot 的自动配置原理是什么？" (边说边传输)
AI: [<1秒后开始回复] "Spring Boot 的自动配置主要依赖..."
你: [AI 说到一半时打断] "等等，先说 @Conditional 注解"
AI: [立即停止，切换话题] "好的，@Conditional 注解是..."
```

| 维度 | 半双工 | 全双工 |
|------|--------|--------|
| 延迟 | 5-15 秒 | <1 秒首字节 |
| 打断 | 不支持 | 随时打断 AI |
| 自然度 | 像发语音消息 | 像面对面聊天 |
| VAD | 手动控制 (按住/松开) | 自动检测说话停顿 |
| 交互模式 | 录音 → 上传 → 等待 → 听 | 自由对话 |

### 1.3 技术方案选择

| 方案 | 优缺点 | 结论 |
|------|--------|------|
| A: WebSocket 流式升级 | 复用现有 ASR+LLM+TTS，延迟仍然 2-5s | 不选 |
| **B: DashScope Realtime** | 端到端单模型，延迟 <1s，内置 VAD | **选定** |
| C: WebRTC/LiveKit | 微信小程序不支持 WebRTC | 不选 |
| D: OpenAI Realtime | 国内网络不稳定，成本高 | 不选 |

---

## 2. 整体架构

```
+----------------------------------------------------------------------+
|                        客户端 (uni-app)                               |
|                                                                       |
|  +---------------+    +------------------+    +--------------------+  |
|  | AudioCapture  |    | RealtimeSocket   |    | AudioPlayback      |  |
|  | (持续采集     |--->| (单一 WS 连接)   |--->| (流式播放          |  |
|  |  PCM 16kHz)   |    | 双向音频+控制    |    |  PCM 24kHz)        |  |
|  +---------------+    +--------+---------+    +--------------------+  |
|                                |                                      |
|  +-----------------------------+-------------------------------------+|
|  |              InterviewRealtimeManager (新)                        ||
|  |  - 管理全双工会话生命周期                                         ||
|  |  - 处理 barge-in (用户打断)                                      ||
|  |  - 降级切换到半双工模式                                           ||
|  +-------------------------------------------------------------------+|
+----------------------------------------------------------------------+
                               |
                          WS (音频帧 + JSON 控制)
                               |
                               v
+----------------------------------------------------------------------+
|                    Spring Boot 后端 (代理层)                          |
|                                                                       |
|  +-------------------------------------------------------------------+|
|  |              RealtimeProxyWebSocketHandler (新)                    ||
|  |                                                                    ||
|  |  客户端 WS <--桥接--> DashScope WS                                ||
|  |                                                                    ||
|  |  职责:                                                             ||
|  |  1. 注入 system prompt (面试官人设 + 当前题目 + 评分标准)         ||
|  |  2. 拦截 AI 回复 -> 提取评分/决策建议                             ||
|  |  3. 驱动面试状态机 (题目推进、追问决策)                            ||
|  |  4. 持久化每轮对话记录                                             ||
|  |  5. API key 隔离 (客户端永远不接触密钥)                           ||
|  +-------------------------------------------------------------------+|
|                               |                                       |
|                               v                                       |
|  +-------------------------------------------------------------------+|
|  |  现有引擎 (复用)                                                   ||
|  |  SimpleInterviewEngine + FollowUpDecisionEngine                    ||
|  |  InterviewSessionState + SessionStore (JDBC)                       ||
|  |  InterviewWebSocketHandler (半双工状态推送，保留)                   ||
|  +-------------------------------------------------------------------+|
+----------------------------------------------------------------------+
                               |
                    DashScope Realtime WS
                               |
                               v
+----------------------------------------------------------------------+
|          DashScope Qwen-Omni-Realtime                                 |
|          wss://dashscope.aliyuncs.com/api-ws/v1/realtime              |
|                                                                       |
|  模型: qwen-omni-turbo-realtime (低延迟，推荐)                        |
|        qwen3.5-omni-flash-realtime (备选)                             |
|                                                                       |
|  能力: 音频输入 -> 理解 -> 文本+音频输出 (端到端)                     |
|        内置 VAD (语音活动检测)                                         |
|        内置 barge-in (用户打断 AI 说话)                                |
|        支持 system prompt 注入                                        |
+----------------------------------------------------------------------+
```

### 2.1 核心设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 代理模式 | 后端中转 (非直连) | API key 安全、注入业务逻辑、可降级 |
| 模型 | `qwen-omni-turbo-realtime` | 延迟最低，面试场景对话质量足够 |
| 与现有系统关系 | 新增并行通道，不替换 | 半双工作为降级方案保留 |
| 状态机 | 复用 `SimpleInterviewEngine` 核心逻辑 | 追问/推进逻辑不变，只是触发方式从 REST 变为 WS 事件 |

### 2.2 关键变化 vs 现有架构

| 维度 | 现有 (半双工) | 全双工 |
|------|--------------|--------|
| 音频传输 | HTTP multipart 上传完整文件 | WS 实时流式传输 PCM 帧 |
| ASR | 独立 DashScope ASR WS 调用 | Qwen-Omni 内置 (端到端) |
| LLM | 独立 OpenAI-compatible REST | Qwen-Omni 内置 (端到端) |
| TTS | 独立 DashScope TTS WS 调用 | Qwen-Omni 内置 (端到端) |
| 延迟 | 录音->上传->ASR->LLM->TTS->下载->播放 (5-15s) | 实时流式 (<1s 首字节) |
| 交互 | 按住说话 -> 等待 -> 听回复 | 自然对话，随时说随时听 |

---

## 3. 客户端实现

### 3.1 新增 Composable: `useRealtimeInterview.ts`

```typescript
// src/composables/useRealtimeInterview.ts
export function useRealtimeInterview() {
  const socket = ref<WebSocket | null>(null)
  const audioContext = ref<AudioContext | null>(null)
  const mediaStream = ref<MediaStream | null>(null)
  const audioWorklet = ref<AudioWorkletNode | null>(null)

  // 状态
  const isConnected = ref(false)
  const isAiSpeaking = ref(false)
  const canInterrupt = ref(true)

  // 音频输出队列 (流式播放)
  const audioQueue: AudioBuffer[] = []
  let currentSource: AudioBufferSourceNode | null = null

  // 连接全双工会话
  async function connect(sessionId: string) {
    // 1. 获取 ticket
    const { ticket } = await api.post(`/api/interviews/${sessionId}/realtime-ticket`)

    // 2. 建立 WebSocket
    const wsUrl = `${WS_BASE}/ws/realtime?ticket=${ticket}`
    socket.value = new WebSocket(wsUrl)

    socket.value.onopen = () => {
      isConnected.value = true
      startAudioCapture()
    }

    socket.value.onmessage = (event) => {
      const msg = JSON.parse(event.data)
      handleServerMessage(msg)
    }
  }

  // 启动音频采集 (持续发送)
  async function startAudioCapture() {
    mediaStream.value = await navigator.mediaDevices.getUserMedia({
      audio: {
        sampleRate: 16000,
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true
      }
    })

    audioContext.value = new AudioContext({ sampleRate: 16000 })
    await audioContext.value.audioWorklet.addModule('/audio-processor.js')

    const source = audioContext.value.createMediaStreamSource(mediaStream.value)
    audioWorklet.value = new AudioWorkletNode(audioContext.value, 'pcm-processor')

    // 监听 PCM 数据块
    audioWorklet.value.port.onmessage = (e) => {
      const pcmChunk = e.data // Float32Array
      sendAudioChunk(pcmChunk)
    }

    source.connect(audioWorklet.value)
  }

  // 发送音频帧到服务器
  function sendAudioChunk(pcm: Float32Array) {
    if (!socket.value || !isConnected.value) return

    // 转 Int16 PCM + Base64
    const int16 = new Int16Array(pcm.length)
    for (let i = 0; i < pcm.length; i++) {
      int16[i] = Math.max(-32768, Math.min(32767, pcm[i] * 32768))
    }
    const base64 = arrayBufferToBase64(int16.buffer)

    socket.value.send(JSON.stringify({
      type: 'audio.append',
      audio: base64
    }))
  }

  // 处理服务器消息
  function handleServerMessage(msg: any) {
    switch (msg.type) {
      case 'audio.delta':
        // AI 音频流式返回
        playAudioDelta(msg.audio) // base64 PCM
        isAiSpeaking.value = true
        break

      case 'audio.done':
        isAiSpeaking.value = false
        break

      case 'transcript.user':
        // 用户说话的实时转写
        emit('userTranscript', msg.text)
        break

      case 'transcript.assistant':
        // AI 回复的文本
        emit('assistantTranscript', msg.text)
        break

      case 'session.updated':
        // 面试状态更新 (题目推进、评分等)
        emit('sessionUpdated', msg.session)
        break

      case 'mode.changed':
        // 后端主动触发降级
        if (msg.mode === 'standard') {
          handleFallbackToHalfDuplex(msg.reason, msg.session)
        }
        break

      case 'realtime.reconnecting':
        emit('status', `重连中 (${msg.attempt}/${msg.maxAttempt})...`)
        break

      case 'realtime.reconnected':
        emit('status', '已恢复实时连接')
        break

      case 'error':
        handleError(msg)
        break
    }
  }

  // 流式播放 AI 音频
  function playAudioDelta(base64Audio: string) {
    const pcmBytes = base64ToInt16Array(base64Audio)
    const float32 = new Float32Array(pcmBytes.length)
    for (let i = 0; i < pcmBytes.length; i++) {
      float32[i] = pcmBytes[i] / 32768
    }

    const buffer = audioContext.value!.createBuffer(1, float32.length, 24000)
    buffer.copyToChannel(float32, 0)

    audioQueue.push(buffer)
    if (!currentSource) {
      playNextInQueue()
    }
  }

  function playNextInQueue() {
    if (audioQueue.length === 0) {
      currentSource = null
      return
    }

    const buffer = audioQueue.shift()!
    currentSource = audioContext.value!.createBufferSource()
    currentSource.buffer = buffer
    currentSource.connect(audioContext.value!.destination)
    currentSource.onended = () => playNextInQueue()
    currentSource.start()
  }

  // 用户打断 AI
  function interrupt() {
    if (currentSource) {
      currentSource.stop()
      currentSource = null
      audioQueue.length = 0
    }

    socket.value?.send(JSON.stringify({
      type: 'conversation.interrupt'
    }))
  }

  // 断开连接
  function disconnect() {
    mediaStream.value?.getTracks().forEach(t => t.stop())
    socket.value?.close()
    audioContext.value?.close()
    isConnected.value = false
  }

  return {
    isConnected,
    isAiSpeaking,
    canInterrupt,
    connect,
    disconnect,
    interrupt
  }
}
```

### 3.2 UI 变化 (session.vue)

```vue
<template>
  <!-- 模式切换 -->
  <div class="mode-selector">
    <button @click="mode = 'half-duplex'" :class="{ active: mode === 'half-duplex' }">
      标准模式 (按住说话)
    </button>
    <button @click="mode = 'full-duplex'" :class="{ active: mode === 'full-duplex' }">
      实时对话 (自动检测)
    </button>
  </div>

  <!-- 半双工 UI (保留) -->
  <div v-if="mode === 'half-duplex'" class="half-duplex-controls">
    <button @touchstart="startRecording" @touchend="stopRecording">
      按住说话
    </button>
  </div>

  <!-- 全双工 UI (新增) -->
  <div v-else class="full-duplex-controls">
    <div class="status-indicator">
      <span v-if="!realtime.isConnected">未连接</span>
      <span v-else-if="realtime.isAiSpeaking" class="ai-speaking">
        AI 正在回答...
        <button @click="realtime.interrupt" class="interrupt-btn">打断</button>
      </span>
      <span v-else class="listening">
        正在聆听 (随时说话)
      </span>
    </div>

    <!-- 实时转写显示 -->
    <div class="live-transcript">
      <p class="user-text">{{ liveUserText }}</p>
      <p class="ai-text">{{ liveAiText }}</p>
    </div>
  </div>
</template>
```

### 3.3 降级策略

```typescript
const FALLBACK_TRIGGERS = {
  wsError: 3,           // WS 连接失败 3 次
  audioContextFail: 1,  // AudioContext 初始化失败
  noMicrophone: 1       // 无麦克风权限
}

// 前置检查
async function checkRealtimeCapability(): Promise<boolean> {
  // 1. 浏览器能力
  if (!window.AudioWorkletNode) return false

  // 2. 麦克风权限
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(t => t.stop())
  } catch {
    return false
  }

  // 3. 后端可用性
  try {
    const { available } = await api.get('/api/interviews/realtime-capability')
    return available
  } catch {
    return false
  }
}

// 降级到半双工
function handleFallbackToHalfDuplex(reason: string, session: any) {
  stopAudioCapture()
  mode.value = 'half-duplex'
  applySessionState(session)
  interviewSocket.connect(session.sessionId)
  uni.showModal({
    title: '模式切换',
    content: `实时对话模式不可用 (${reason})，已自动切换为标准模式。面试进度已保留。`,
    showCancel: false
  })
}
```

---

## 4. 后端代理层

### 4.1 RealtimeProxyWebSocketHandler

```java
@Component
public class RealtimeProxyWebSocketHandler extends TextWebSocketHandler {

    // 双向连接映射
    private final ConcurrentHashMap<String, ProxySession> sessions = new ConcurrentHashMap<>();

    private final DashScopeProperties dashScopeProperties;
    private final RealtimeInterviewStateMachine stateMachine;
    private final InterviewSessionStore sessionStore;
    private final RealtimeFallbackService fallbackService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        String userId = (String) clientSession.getAttributes().get("userId");
        String sessionId = (String) clientSession.getAttributes().get("sessionId");

        // 1. 加载面试会话状态
        InterviewSessionState state = sessionStore.findById(sessionId)
            .orElseThrow(() -> new IllegalStateException("Session not found"));

        // 2. 建立到 DashScope 的 WebSocket 连接
        OmniRealtimeConversation dashscopeWs = new OmniRealtimeConversation(
            buildDashScopeParam(),
            new ProxyCallback(clientSession, state)
        );
        dashscopeWs.connect();

        // 3. 注入 system prompt (面试上下文)
        dashscopeWs.updateSession(buildSessionConfig(state));

        // 4. 保存代理会话
        ProxySession proxySession = new ProxySession(clientSession, dashscopeWs, state);
        sessions.put(clientSession.getId(), proxySession);

        // 5. 通知客户端已就绪
        sendToClient(clientSession, Map.of("type", "session.ready"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message)
            throws Exception {
        ProxySession proxy = sessions.get(clientSession.getId());
        if (proxy == null) return;

        JsonNode msg = objectMapper.readTree(message.getPayload());
        String type = msg.path("type").asText();

        switch (type) {
            case "audio.append" -> {
                String base64Audio = msg.path("audio").asText();
                proxy.dashscopeWs().appendAudio(base64Audio);
            }
            case "conversation.interrupt" -> {
                proxy.dashscopeWs().cancelResponse();
                proxy.state().setLastInterruptedAt(System.currentTimeMillis());
                sendToClient(clientSession, Map.of("type", "interrupt.ack"));
            }
            case "conversation.commit" -> {
                proxy.dashscopeWs().commit();
            }
            case "PING" -> {
                sendToClient(clientSession, Map.of("type", "PONG"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) {
        ProxySession proxy = sessions.remove(clientSession.getId());
        if (proxy != null) {
            try { proxy.dashscopeWs().close(); } catch (Exception ignored) {}
        }
    }

    // ========== DashScope 配置构建 ==========

    private OmniRealtimeParam buildDashScopeParam() {
        return OmniRealtimeParam.builder()
            .model("qwen-omni-turbo-realtime")
            .url(dashScopeProperties.getBaseUrl())
            .apikey(dashScopeProperties.getApiKey())
            .build();
    }

    private OmniRealtimeConfig buildSessionConfig(InterviewSessionState state) {
        return OmniRealtimeConfig.builder()
            .modalities(List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO))
            .enableTurnDetection(true)
            .transcriptionConfig(OmniRealtimeTranscriptionParam.builder()
                .language("zh")
                .inputSampleRate(16000)
                .inputAudioFormat("pcm")
                .build())
            .voice("Cherry")
            .instructions(buildSystemPrompt(state))
            .build();
    }

    private record ProxySession(
        WebSocketSession clientSession,
        OmniRealtimeConversation dashscopeWs,
        InterviewSessionState state
    ) {}
}
```

### 4.2 DashScope 回调处理 (ProxyCallback)

```java
private class ProxyCallback implements OmniRealtimeCallback {
    private final WebSocketSession clientSession;
    private final InterviewSessionState state;
    private final StringBuilder currentAiText = new StringBuilder();
    private final StringBuilder currentUserText = new StringBuilder();

    // 指标采集
    private long lastAiResponseDoneAt;
    private long lastUserSpeechStartAt;
    private final List<Long> responseLatencies = new ArrayList<>();
    private int interruptCount;

    @Override
    public void onEvent(JsonObject event) {
        String type = event.get("type").getAsString();

        switch (type) {
            case "conversation.item.input_audio_transcription.delta" -> {
                String delta = event.get("delta").getAsString();
                if (currentUserText.isEmpty()) {
                    lastUserSpeechStartAt = System.currentTimeMillis();
                    if (lastAiResponseDoneAt > 0) {
                        responseLatencies.add(lastUserSpeechStartAt - lastAiResponseDoneAt);
                    }
                }
                currentUserText.append(delta);
                forwardToClient("transcript.user.delta", Map.of("delta", delta));
            }

            case "conversation.item.input_audio_transcription.completed" -> {
                String fullText = event.get("transcript").getAsString();
                currentUserText.setLength(0);
                currentUserText.append(fullText);
                forwardToClient("transcript.user.completed", Map.of("text", fullText));
            }

            case "response.audio.delta" -> {
                String audioDelta = event.get("delta").getAsString();
                forwardToClient("audio.delta", Map.of("audio", audioDelta));
            }

            case "response.text.delta" -> {
                String textDelta = event.get("delta").getAsString();
                currentAiText.append(textDelta);
                forwardToClient("transcript.assistant.delta", Map.of("delta", textDelta));
            }

            case "response.done" -> {
                lastAiResponseDoneAt = System.currentTimeMillis();
                forwardToClient("audio.done", Map.of());
                handleAiResponseComplete();
            }

            case "error" -> {
                JsonObject error = event.getAsJsonObject("error");
                forwardToClient("error", Map.of(
                    "code", error.get("code").getAsString(),
                    "message", error.get("message").getAsString()
                ));
            }
        }
    }

    private void handleAiResponseComplete() {
        String userAnswer = currentUserText.toString().trim();
        String aiReply = currentAiText.toString().trim();
        if (userAnswer.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                TurnResult result = stateMachine.processTurnComplete(
                    state, userAnswer, aiReply
                );

                forwardToClient("session.updated", Map.of("session", result.view()));

                switch (result.action()) {
                    case CONTINUE -> { /* DashScope 继续监听 */ }
                    case UPDATE_PROMPT -> {
                        OmniRealtimeConfig newConfig = OmniRealtimeConfig.builder()
                            .instructions(result.newSystemPrompt())
                            .build();
                        proxy.dashscopeWs().updateSession(newConfig);
                    }
                    case DISCONNECT -> {
                        forwardToClient("session.completed", Map.of("session", result.view()));
                        proxy.dashscopeWs().close();
                    }
                }
            } catch (Exception e) {
                log.error("Turn processing failed", e);
                forwardToClient("error", Map.of(
                    "code", "TURN_PROCESS_ERROR",
                    "message", "面试轮次处理失败"
                ));
            }
        });

        currentUserText.setLength(0);
        currentAiText.setLength(0);
    }

    @Override
    public void onClose(int code, String reason) {
        reconnector.onDashScopeDisconnected(proxy, code, reason);
    }
}
```

### 4.3 System Prompt 动态构建

```java
private String buildSystemPrompt(InterviewSessionState state) {
    InterviewQuestionSnapshot currentQ = state.getCurrentQuestion();
    InterviewStage stage = state.getStage();

    StringBuilder prompt = new StringBuilder();

    // 1. 角色设定
    prompt.append("你是一位专业的 Java 技术面试官，正在进行一场实时语音面试。\n\n");

    // 2. 当前阶段
    prompt.append("## 当前阶段\n");
    prompt.append(switch (stage) {
        case OPENING -> "开场阶段 - 营造轻松氛围，建立信任";
        case JAVA_CORE -> "Java 核心技术考察 - 深入技术细节";
        case PROJECT_DEEP_DIVE -> "项目深挖阶段 - 结合实战经验";
        case WRAP_UP -> "收尾阶段 - 总结并鼓励候选人";
    });
    prompt.append("\n\n");

    // 3. 当前题目
    if (stage != InterviewStage.WRAP_UP) {
        prompt.append("## 当前题目\n");
        prompt.append("**题目**: ").append(currentQ.title()).append("\n");
        prompt.append("**考察点**: ").append(currentQ.prompt()).append("\n");
        if (currentQ.expectedPoints() != null && !currentQ.expectedPoints().isEmpty()) {
            prompt.append("**期望要点**: \n");
            currentQ.expectedPoints().forEach(p ->
                prompt.append("- ").append(p).append("\n")
            );
        }
        prompt.append("\n");

        // 4. 追问策略
        int followUpIndex = state.getFollowUpIndex();
        int maxFollowUp = state.getMaxFollowUpPerQuestion();
        prompt.append("## 追问策略\n");
        prompt.append("当前已追问 ").append(followUpIndex)
              .append(" 次，最多 ").append(maxFollowUp).append(" 次。\n");
        prompt.append("根据候选人回答质量决定：\n");
        prompt.append("- 回答完整且深入 -> 进入下一题\n");
        prompt.append("- 回答浅显或遗漏要点 -> 追问细节\n");
        prompt.append("- 回答明显错误 -> 引导纠正\n\n");
    } else {
        prompt.append("## 面试结束\n");
        prompt.append("请用 1-2 句话自然地结束面试，鼓励候选人，不要过于冗长。\n\n");
    }

    // 5. 对话风格
    prompt.append("## 对话风格\n");
    prompt.append("- 语速适中，吐字清晰\n");
    prompt.append("- 语气专业但友好，避免生硬\n");
    prompt.append("- 回复简洁，每次 2-3 句话即可\n");
    prompt.append("- 允许候选人随时打断你\n");

    return prompt.toString();
}
```

### 4.4 配置注册

```java
@Configuration
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeProxyHandler, "/ws/realtime")
            .addInterceptors(realtimeHandshakeInterceptor)
            .setAllowedOrigins("*");
    }
}
```

### 4.5 REST API 扩展

```java
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    // 新增：检查全双工可用性
    @GetMapping("/realtime-capability")
    public RealtimeCapability checkRealtimeCapability() {
        return fallbackService.checkCapability();
    }

    // 新增：获取全双工 ticket
    @PostMapping("/{sessionId}/realtime-ticket")
    public InterviewWsTicket getRealtimeTicket(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        InterviewSessionState state = sessionStore.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found"));

        if (!state.getOwnerUserId().equals(user.getUserId())) {
            throw new ForbiddenException("Not your session");
        }

        return ticketService.issueRealtimeTicket(user.getUserId(), sessionId);
    }
}
```

---

## 5. 状态机升级

### 5.1 全双工状态流转

```
+----------+  connect()   +--------------+
| PREVIEW  |------------->| CONNECTING   |
+----------+              +------+-------+
                                 | session.ready
                                 v
                          +--------------+
                 +------->|REALTIME_ACTIVE|<----------+
                 |        +--+--------+--+            |
                 |           |        |               |
   response.done |           |        | interrupt     |
    FOLLOW_UP    |           |        |               |
                 |           v        v               |
                 |  +--------+--+ +---+----------+    |
                 +--+TURN_      | |AI_INTERRUPTED|    |
                    |ANALYZING  | |(等待用户继续)|    |
                    +--+-----+--+ +------+-------+    |
                       |     |           | 用户继续说  |
            NEXT_Q     |     +-----------+------------+
                       v
                 +-----+----------+
                 |QUESTION_       |
                 |ADVANCING       |
                 |(更新 prompt    |
                 | 注入新题目)    |
                 +-----+----------+
                       | updateSession() 完成
                       +-----> REALTIME_ACTIVE (循环)

  END_INTERVIEW --> COMPLETED / CANCELLED
```

### 5.2 RealtimeInterviewStateMachine

```java
@Component
public class RealtimeInterviewStateMachine {

    private final FollowUpDecisionEngine decisionEngine;
    private final InterviewAnswerAnalyzer analyzer;
    private final InterviewSessionStore sessionStore;

    /**
     * AI 回复完成时调用 -- 分析用户回答并决定下一步
     */
    public TurnResult processTurnComplete(
            InterviewSessionState state,
            String userAnswer,
            String aiReply
    ) {
        // 1. 记录用户回答
        state.appendRealtimeUserAnswer(userAnswer);

        // 2. 分析回答质量 (复用现有 heuristic 分析器)
        AnswerEvidence evidence = analyzer.analyze(
            state.getCurrentQuestion().prompt(),
            userAnswer,
            state.getCurrentQuestion().expectedPoints()
        );

        // 3. 决策：追问 / 下一题 / 结束
        FollowUpDecision decision = decisionEngine.decide(
            state.getCurrentQuestion(),
            state.getStage(),
            state.getFollowUpIndex(),
            state.getMaxFollowUpPerQuestion(),
            evidence
        );

        // 4. 执行状态变更
        switch (decision.action()) {
            case FOLLOW_UP -> {
                state.setFollowUpIndex(state.getFollowUpIndex() + 1);
                state.appendRealtimeAiReply(aiReply, evidence, decision);
                sessionStore.save(state);
                return new TurnResult(TurnAction.CONTINUE, null, state.toView());
            }

            case NEXT_QUESTION -> {
                state.appendRealtimeAiReply(aiReply, evidence, decision);
                int nextIndex = state.getCurrentQuestionIndex() + 1;
                if (nextIndex >= state.getQuestions().size()) {
                    return processComplete(state);
                }
                state.setCurrentQuestionIndex(nextIndex);
                state.setFollowUpIndex(0);
                state.setStage(resolveStage(state));
                sessionStore.save(state);
                return new TurnResult(
                    TurnAction.UPDATE_PROMPT,
                    buildSystemPrompt(state),
                    state.toView()
                );
            }

            case END_INTERVIEW -> {
                return processComplete(state);
            }
        }

        throw new IllegalStateException("Unknown action: " + decision.action());
    }

    private TurnResult processComplete(InterviewSessionState state) {
        state.setStatus("COMPLETED");
        state.setStage(InterviewStage.WRAP_UP);
        sessionStore.save(state);
        return new TurnResult(TurnAction.UPDATE_PROMPT, buildWrapUpPrompt(), state.toView());
    }

    private InterviewStage resolveStage(InterviewSessionState state) {
        int idx = state.getCurrentQuestionIndex();
        int total = state.getQuestions().size();
        if (idx == 0) return InterviewStage.OPENING;
        if (idx <= Math.max(1, total / 2)) return InterviewStage.JAVA_CORE;
        return InterviewStage.PROJECT_DEEP_DIVE;
    }

    public record TurnResult(
        TurnAction action,
        String newSystemPrompt,
        InterviewSessionView view
    ) {}

    public enum TurnAction {
        CONTINUE,
        UPDATE_PROMPT,
        DISCONNECT
    }
}
```

### 5.3 InterviewSessionState 扩展

```java
public class InterviewSessionState {
    // ... 现有字段 ...

    /** 面试模式: "standard"(半双工) / "realtime"(全双工) */
    private String interviewMode = "standard";

    /** 最后一次打断时间戳 */
    private Long lastInterruptedAt;

    /** 全双工对话的总轮次计数 */
    private int realtimeTurnCount;

    /** 全双工统计指标 (仅 realtime 模式填充) */
    private RealtimeMetrics realtimeMetrics;

    public void appendRealtimeUserAnswer(String text) {
        InterviewRoundRecord round = new InterviewRoundRecord(
            currentQuestionIndex, followUpIndex,
            "USER", text, null, null, "REALTIME", null, null
        );
        rounds.add(round);
        realtimeTurnCount++;
    }

    public void appendRealtimeAiReply(
            String text, AnswerEvidence evidence, FollowUpDecision decision) {
        InterviewRoundRecord round = new InterviewRoundRecord(
            currentQuestionIndex, followUpIndex,
            "ASSISTANT", text, null, null, "REALTIME", evidence, decision
        );
        rounds.add(round);
    }

    public static class RealtimeMetrics {
        private int totalTurns;
        private int interruptCount;
        private long avgResponseLatencyMs;
        private long effectiveDurationMs;
        private long realtimeStartedAt;
        private long realtimeEndedAt;
    }
}
```

---

## 6. 降级策略与错误处理

### 6.1 降级场景

```
用户选择 "实时对话" 模式
    |
    v
[检查前置条件]
    |
    +-- 浏览器不支持 AudioWorklet? --> 直接降级到半双工
    +-- 无麦克风权限? --> 直接降级到半双工
    +-- DashScope Realtime 不可用? --> 直接降级到半双工
    |
    v (前置条件通过)
[建立全双工连接]
    |
    +-- WS 连接失败 (3次重试后) --> 降级到半双工
    +-- DashScope WS 连接失败 --> 降级到半双工
    |
    v (连接成功)
[全双工面试进行中]
    |
    +-- DashScope WS 断开 --> 尝试重连 (2次)
    |     +-- 重连失败 --> 热降级到半双工 (保留面试进度)
    +-- 客户端 WS 断开 --> 自动重连 2 次
    |     +-- 重连失败 --> 半双工恢复 (现有 restore 机制)
    +-- 音频流超时 (30s) --> 发送心跳提醒
    +-- DashScope 429/503 --> 热降级到半双工
```

### 6.2 RealtimeFallbackService

```java
@Service
public class RealtimeFallbackService {

    private final InterviewSessionStore sessionStore;

    /**
     * 热降级：全双工 -> 半双工，保留面试进度
     */
    public FallbackResult fallbackToHalfDuplex(InterviewSessionState state, String reason) {
        state.setInterviewMode("standard");
        state.appendSystemMessage(
            "系统提示：实时对话模式因 [" + reason + "] 已切换为标准模式，面试进度已保留。"
        );
        sessionStore.save(state);
        return new FallbackResult(state.getSessionId(), state.toView(), reason);
    }

    /**
     * 前置条件检查
     */
    public RealtimeCapability checkCapability() {
        boolean available = isDashScopeRealtimeAvailable();
        return new RealtimeCapability(
            available,
            available ? "全双工模式可用" : "DashScope Realtime 服务不可用，将使用标准模式"
        );
    }

    private boolean isDashScopeRealtimeAvailable() {
        try {
            OmniRealtimeConversation probe = new OmniRealtimeConversation(
                buildProbeParam(),
                new OmniRealtimeCallback() {
                    @Override public void onEvent(JsonObject msg) {}
                    @Override public void onClose(int code, String reason) {}
                }
            );
            probe.connect();
            probe.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record FallbackResult(String sessionId, InterviewSessionView view, String reason) {}
    public record RealtimeCapability(boolean available, String message) {}
}
```

### 6.3 错误分类

| 错误类型 | 表现 | 处理 | 用户感知 |
|----------|------|------|----------|
| DashScope 连接失败 | WS open 超时 | 重连 2 次 -> 降级 | "连接中..." -> "已切换标准模式" |
| DashScope 限流 (429) | error 事件 | 立即降级 | "服务繁忙，已切换标准模式" |
| DashScope 服务异常 (503) | error 事件 | 重连 1 次 -> 降级 | 同上 |
| 客户端 WS 断开 | onclose | 自动重连 2 次 | "重连中..." |
| 音频采集异常 | getUserMedia 失败 | 降级 | "麦克风不可用" |
| 音频流超时 | 30s 无 audio.append | 心跳提醒 | "检测到静音，是否继续？" |

### 6.4 零数据丢失保证

- 每轮 `response.done` -> `sessionStore.save()` (持久化)
- 降级时保留所有 rounds (兼容格式，`answerMode = "REALTIME"`)
- 客户端断线可通过 `GET /state` + WS 重连恢复
- 服务端重启 -> JDBC 存储，状态不丢失

---

## 7. 报告生成集成

### 7.1 全双工特有指标

```java
public static class RealtimeMetrics {
    private int totalTurns;            // 总对话轮次
    private int interruptCount;        // 用户打断次数
    private long avgResponseLatencyMs; // 平均用户响应延迟 (ms)
    private long effectiveDurationMs;  // 实际对话时长 (排除静音)
    private long realtimeStartedAt;    // 全双工开始时间
    private long realtimeEndedAt;      // 全双工结束时间
}
```

### 7.2 报告流程

全双工模式下复用现有 `ReportService`，额外增加:

1. 收集所有 `REALTIME` 模式 rounds
2. 计算各维度得分 (复用现有评分逻辑)
3. 全双工特有指标 (响应延迟、打断次数、对话轮次)
4. LLM 综合评语增加 `interviewMode` 字段，全双工下额外评估 "表达流畅度"

---

## 8. WebSocket 消息协议总览

### 8.1 客户端 -> 服务端

| 消息类型 | 说明 |
|----------|------|
| `{ type: "audio.append", audio: "<base64>" }` | 持续发送音频帧 |
| `{ type: "conversation.interrupt" }` | 用户打断 AI |
| `{ type: "conversation.commit" }` | 手动提交 (可选) |
| `{ type: "PING" }` | 心跳 |

### 8.2 服务端 -> 客户端

| 消息类型 | 说明 |
|----------|------|
| `{ type: "session.ready" }` | 连接就绪 |
| `{ type: "audio.delta", audio: "<base64>" }` | AI 音频流 |
| `{ type: "audio.done" }` | AI 音频结束 |
| `{ type: "transcript.user.delta", delta: "..." }` | 用户实时转写 |
| `{ type: "transcript.user.completed", text: "..." }` | 用户转写完成 |
| `{ type: "transcript.assistant.delta", delta: "..." }` | AI 文本流 |
| `{ type: "session.updated", session: {...} }` | 面试状态变更 |
| `{ type: "session.completed", session: {...} }` | 面试结束 |
| `{ type: "mode.changed", mode: "standard", ... }` | 降级通知 |
| `{ type: "realtime.reconnecting", attempt: 1 }` | 重连中 |
| `{ type: "realtime.reconnected" }` | 重连成功 |
| `{ type: "interrupt.ack" }` | 打断确认 |
| `{ type: "error", code: "...", message: "..." }` | 错误 |
| `{ type: "PONG" }` | 心跳回复 |

---

## 9. REST API 总览

```
现有 API (保留不变)
  POST   /api/interviews                     创建面试
  POST   /api/interviews/{id}/answer         提交回答 (半双工)
  POST   /api/interviews/{id}/skip           跳过问题 (半双工)
  POST   /api/interviews/{id}/end            结束面试
  GET    /api/interviews/{id}/state           获取状态
  POST   /api/interviews/{id}/ws-ticket       半双工 WS ticket
  WS     /ws/interview?ticket=xxx             半双工状态推送

新增 API
  GET    /api/interviews/realtime-capability  检查全双工可用性
  POST   /api/interviews/{id}/realtime-ticket 全双工 WS ticket
  WS     /ws/realtime?ticket=xxx              全双工音频+控制通道
```

---

## 10. 实施分期

### Phase 0: PoC 验证 (3-5 天)

- 0.1 独立 Java demo: 建立 WS -> 发送音频 -> 收到回复音频
- 0.2 独立 H5 demo: AudioWorklet 采集 -> WS 发送 -> 播放返回
- 0.3 组合: H5 -> Spring Boot proxy -> DashScope -> 音频回传
- 0.4 测量: 首字节延迟、音频质量、VAD 准确性
- **判定标准**: 首字节延迟 < 2s 且 VAD 正常工作 -> 继续；否则评估备选方案

### Phase 1: 后端核心 (5-7 天)

- 1.1 DashScopeProperties 扩展 (新增 realtime 端点配置)
- 1.2 RealtimeProxyWebSocketHandler (代理桥接)
- 1.3 RealtimeInterviewStateMachine (事件驱动状态机)
- 1.4 System Prompt 动态构建 (含题目注入)
- 1.5 RealtimeFallbackService (降级逻辑)
- 1.6 Ticket 扩展 + REST API 新增
- 1.7 InterviewSessionState 扩展 (模式字段 + 指标)

### Phase 2: 客户端实现 (5-7 天)

- 2.1 AudioWorklet PCM 采集处理器
- 2.2 useRealtimeInterview composable
- 2.3 流式音频播放器 (PCM queue + AudioBufferSource)
- 2.4 session.vue 双模式 UI (模式切换 + 全双工控件)
- 2.5 降级处理 + 前置检查
- 2.6 实时转写显示组件

### Phase 3: 集成与打磨 (3-5 天)

- 3.1 报告生成适配 (全双工指标 + 评语)
- 3.2 端到端测试 (完整面试流程)
- 3.3 降级流程验证 (模拟各种断线场景)
- 3.4 微信小程序兼容性测试 (小程序不支持 AudioWorklet -> 降级到半双工)
- 3.5 延迟优化 + 音质调优
- 3.6 文档更新

**预计总工期: 16-24 天**

---

## 11. 文件变更清单

### 新增文件 (约 10 个)

**后端:**
- `RealtimeProxyWebSocketHandler.java` - 全双工代理处理器
- `RealtimeWebSocketConfig.java` - WS 注册配置
- `RealtimeInterviewStateMachine.java` - 事件驱动状态机
- `RealtimeFallbackService.java` - 降级服务
- `RealtimeMetrics.java` - 全双工指标

**前端:**
- `useRealtimeInterview.ts` - 全双工 composable
- `audio-processor.js` - AudioWorklet 处理器
- `RealtimeControls.vue` - 全双工 UI 组件
- `LiveTranscript.vue` - 实时转写显示

**文档:**
- `docs/archive/superpowers/specs/2026-04-15-full-duplex-realtime-voice-design.md` - 本文档

### 修改文件 (约 8 个)

**后端:**
- `DashScopeProperties.java` - 新增 realtime 配置
- `InterviewSessionState.java` - 新增模式/指标字段
- `InterviewController.java` - 新增 2 个 API
- `InterviewWsTicketService.java` - 新增 realtime ticket

**前端:**
- `session.vue` - 双模式切换 UI
- `useInterviewSession.ts` - 创建会话时传 mode
- `interviewApi.ts` - 新增 API 调用
- `types/interview.d.ts` - 类型扩展

### 核心复用率

决策引擎、分析器、持久化层、状态结构全部复用，仅触发方式和音频通道是新的。
复用率约 **70%**。
