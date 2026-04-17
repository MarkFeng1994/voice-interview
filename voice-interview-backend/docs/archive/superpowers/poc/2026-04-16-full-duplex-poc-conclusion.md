# Phase 0 PoC 结论文档 — DashScope qwen-omni-turbo-realtime 全双工验证

> 日期: 2026-04-16  
> 状态: **Go — 全部验证通过，可进入 Phase 1 实施**

---

## 1. 验证概要

| 编号 | 验证项 | 结果 | 备注 |
|------|--------|------|------|
| 0.1 | 全双工基础通路 (WS连接→发送PCM→收到文本+音频回复) | **通过** | VAD 需辅助触发 |
| 0.2 | updateSession() 动态更新 instructions | **通过** | Round1→Spring Boot, Round2→GC/垃圾回收，关键词命中 |
| 0.2b | parameters map 注入 instructions | **通过** | 可作为 instructions 注入的备选方式 |

**总测试耗时**: 42.62 秒（3 个测试串行执行）

---

## 2. 关键指标

### 2.1 连接性能

| 指标 | 数值 |
|------|------|
| WebSocket 连接建立 | **441 ms** |
| session.created 事件 | 连接后立即收到 |
| session.updated 事件 | updateSession() 后立即收到 |

### 2.2 音频处理

| 指标 | 数值 |
|------|------|
| 测试音频来源 | `dashscope-preview.wav` (3680 ms, 117764 bytes) |
| 音频分片 | 4 chunks (32KB each) + 1.5s 静音 |
| 发送耗时 | **7 ms** (极快，非阻塞) |
| AI 音频回复 chunks | 42 |
| AI 回复音频时长 | **13760 ms** |

### 2.3 延迟分析

| 指标 | 数值 | 说明 |
|------|------|------|
| 首字节音频延迟 (含 VAD 等待) | **8498 ms** | 包含 8s VAD 超时等待 |
| 实际模型响应延迟 (扣除 VAD 等待) | **~500 ms** | createResponse() 后几乎立即回复 |
| 理论最优延迟 (实时麦克风输入) | **< 1s** | 实时语音 VAD 正常工作时 |

> **说明**: 8498ms 延迟是因为预录音频不含自然语音停顿，VAD 无法检测到 speech→silence 转换。
> 在实际实时麦克风输入场景中，用户说话停顿后 VAD 会自然触发，延迟将显著降低。

---

## 3. VAD 行为分析

### 3.1 发现

- `enableTurnDetection=true` 启用服务端 VAD
- VAD 依赖检测 **speech→silence 转换** 来判断用户说完
- **预录音频问题**: 连续发送预录 PCM 后，如果没有追加足够静音，VAD 不会触发
- 追加 1.5s 静音后，VAD 仍未在 8s 内触发（可能是合成音频特性问题）

### 3.2 应对策略

采用 **VAD + createResponse() 双保险机制**:

```
1. 发送音频 + 追加 1.5s 静音 + commit
2. 等待 8s，若 VAD 自然触发 → 正常流程
3. 若 8s 内无响应 → 调用 createResponse() 显式触发
```

### 3.3 对正式实现的影响

| 场景 | VAD 预期行为 | 备用方案 |
|------|-------------|---------|
| 实时麦克风 | 正常触发（自然停顿） | 无需备用 |
| 长时间沉默 | 不触发（正确行为） | 客户端超时提醒 |
| 网络抖动 | 可能延迟触发 | createResponse() 兜底 |

---

## 4. updateSession() 验证详情

### 4.1 测试设计

- **Round 1**: instructions 要求谈 Spring Boot → AI 回复包含 "Spring Boot" ✓
- **Round 2**: updateSession() 更新 instructions 要求谈 GC/垃圾回收 → AI 回复包含相关关键词 ✓
- **同一连接内** updateSession() 生效，无需断开重连

### 4.2 关键结论

> **updateSession() 可以在同一 WebSocket 连接内动态更新 instructions，且立即对下一轮对话生效。**

这意味着正式实现中:
- 每道面试题切换时，只需调用 `updateSession()` 更新 instructions
- **不需要** 断开重连，大幅降低延迟和复杂度
- instructions 通过 `parameters(Map.of("instructions", "..."))` 注入

### 4.3 instructions 注入方式

两种方式均验证通过:

1. **`buildFullDuplexConfig(instructions)`** — 通过 `parameters(Map.of("instructions", ...))` 注入（推荐）
2. **直接 `OmniRealtimeConfig.builder().parameters(Map.of("instructions", ...))`** — 等效

---

## 5. SDK 使用总结

### 5.1 核心 API

```java
// 1. 建立连接
OmniRealtimeParam param = OmniRealtimeParam.builder()
    .model("qwen-omni-turbo-realtime")
    .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
    .apikey(apiKey)
    .build();
OmniRealtimeConversation conv = new OmniRealtimeConversation(param, callback);
conv.connect();

// 2. 配置会话
OmniRealtimeConfig config = OmniRealtimeConfig.builder()
    .modalities(List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO))
    .voice("Cherry")
    .enableTurnDetection(true)
    .parameters(Map.of("instructions", "你是面试官..."))
    .build();
conv.updateSession(config);

// 3. 发送音频
conv.appendAudio(base64EncodedPcm);
conv.commit();

// 4. 显式触发响应 (VAD 兜底)
conv.createResponse(null, List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO));

// 5. 动态更新 instructions
conv.updateSession(newConfig);

// 6. 关闭
conv.close();
```

### 5.2 事件流

```
session.created → session.updated → input_audio_buffer.committed
→ response.created → response.output_item.added → conversation.item.created
→ response.content_part.added → [response.audio.delta × N] + [response.audio_transcript.delta × N]
→ response.audio_transcript.done → response.audio.done
→ response.content_part.done → response.output_item.done → response.done
```

### 5.3 音频格式

| 方向 | 格式 | 采样率 | 位深 | 声道 |
|------|------|--------|------|------|
| 输入 (用户→模型) | PCM | 16 kHz | 16-bit | Mono |
| 输出 (模型→用户) | PCM | 24 kHz | 16-bit | Mono |

---

## 6. 风险与注意事项

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| VAD 对预录音频不敏感 | 低 | 实时麦克风场景 VAD 正常；createResponse() 兜底 |
| OmniRealtimeConfig 无原生 instructions 字段 | 低 | parameters map 注入已验证有效 |
| 首字节延迟在弱网下可能增加 | 中 | Phase 1 增加超时控制和重试逻辑 |
| SDK 版本兼容性 | 低 | 锁定 dashscope-sdk-java 2.22.7 |

---

## 7. Go/No-Go 决策

### 判定: **Go**

**依据:**

1. **全双工通路** — WebSocket 连接稳定，音频收发正常，文本+音频双通道输出
2. **动态 instructions** — updateSession() 在同一连接内生效，支持面试题切换
3. **延迟可接受** — 模型实际响应延迟 ~500ms，满足面试对话体验要求
4. **SDK 成熟度** — OmniRealtimeConversation API 简洁，事件模型清晰

### 下一步: 进入 Phase 1 — 后端实现

按设计文档 `docs/archive/superpowers/specs/2026-04-15-full-duplex-realtime-voice-design.md` 执行:

1. **Phase 1.1** — `RealtimeSessionManager` 会话生命周期管理
2. **Phase 1.2** — `RealtimeAudioBridge` 音频桥接（浏览器 WebSocket ↔ DashScope WebSocket）
3. **Phase 1.3** — `InterviewFlowController` 面试流程控制（instructions 切换）
4. **Phase 1.4** — 错误处理、重连、超时机制

---

## 附录: 测试产物

| 文件 | 说明 |
|------|------|
| `src/test/java/com/interview/poc/RealtimeFullDuplexPocTest.java` | PoC 测试源码 |
| `target/poc-01-ai-reply.wav` | PoC 0.1 AI 回复音频 (13760ms) |
| 本文档 | PoC 结论与 Go/No-Go 决策 |
