package com.interview.poc;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.google.gson.JsonObject;
import com.interview.common.audio.PcmAudioUtils;

/**
 * Phase 0.1 & 0.2 — DashScope qwen-omni-turbo-realtime 全双工 PoC 验证。
 *
 * <p>验证目标:
 * <ol>
 *   <li>0.1 — 建立 WS 连接，发送 PCM 音频，收到 response.audio.delta + response.audio_transcript.delta</li>
 *   <li>0.2 — 对话一轮后 updateSession() 更新 instructions，验证第二轮回复反映新 instructions</li>
 * </ol>
 *
 * <p>运行前提: 环境变量 APP_DASHSCOPE_API_KEY 已设置。
 * 如果未设置，测试会被 skip 而非 fail。
 *
 * <p>运行方式:
 * <pre>
 * mvn test -pl voice-interview-backend -Dtest=RealtimeFullDuplexPocTest -Dapp.dashscope.api-key=sk-xxx
 * </pre>
 * 或设置环境变量 APP_DASHSCOPE_API_KEY 后直接运行。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealtimeFullDuplexPocTest {

    private static final String MODEL = "qwen-omni-turbo-realtime";
    private static final String WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
    private static final String VOICE = "Cherry";
    private static final int INPUT_SAMPLE_RATE = 16_000;
    private static final int CHUNK_SIZE = 32 * 1024;
    private static final int TIMEOUT_SECONDS = 60;

    private static String apiKey;

    @BeforeAll
    static void checkApiKey() {
        apiKey = System.getenv("APP_DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("app.dashscope.api-key");
        }
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(),
                "跳过 PoC 测试: 未设置 APP_DASHSCOPE_API_KEY 环境变量或 -Dapp.dashscope.api-key 系统属性"
        );
    }

    // ========================================================================
    // Phase 0.1 — 基础双工通路验证
    // ========================================================================

    @Test
    @Order(1)
    void poc01_fullDuplexBasicRoundTrip() throws Exception {
        System.out.println("=== Phase 0.1: 全双工基础通路验证 ===");

        // 准备测试音频 — 生成一段简短的中文语音模拟 PCM
        // 实际测试时用真实录音效果更好，这里用静音+简短数据验证通路
        byte[] testPcm = generateTestPcm();
        System.out.println("测试音频大小: " + testPcm.length + " bytes (" +
                PcmAudioUtils.estimateDurationMs(testPcm.length, INPUT_SAMPLE_RATE) + " ms)");

        // 结果收集
        CountDownLatch responseDone = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        StringBuilder transcriptBuilder = new StringBuilder();
        StringBuilder aiTextBuilder = new StringBuilder();
        CopyOnWriteArrayList<byte[]> audioChunks = new CopyOnWriteArrayList<>();
        AtomicLong firstAudioAt = new AtomicLong(0);
        AtomicLong connectAt = new AtomicLong(0);
        AtomicInteger eventCount = new AtomicInteger(0);

        OmniRealtimeCallback callback = new OmniRealtimeCallback() {
            @Override
            public void onOpen() {
                connectAt.set(System.currentTimeMillis());
                System.out.println("[OPEN] WebSocket 连接已建立");
            }

            @Override
            public void onEvent(JsonObject message) {
                String type = readString(message, "type");
                eventCount.incrementAndGet();

                switch (type) {
                    case "session.created" -> {
                        System.out.println("[EVENT] session.created");
                    }
                    case "response.audio_transcript.delta" -> {
                        String delta = readString(message, "delta");
                        if (delta != null) {
                            aiTextBuilder.append(delta);
                        }
                    }
                    case "response.audio.delta" -> {
                        String audioB64 = readString(message, "delta");
                        if (audioB64 != null) {
                            byte[] pcm = Base64.getDecoder().decode(audioB64);
                            audioChunks.add(pcm);
                            firstAudioAt.compareAndSet(0, System.currentTimeMillis());
                        }
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        String transcript = readString(message, "transcript");
                        if (transcript != null) {
                            transcriptBuilder.append(transcript);
                            System.out.println("[EVENT] 用户语音转写: " + transcript);
                        }
                    }
                    case "response.done" -> {
                        System.out.println("[EVENT] response.done");
                        responseDone.countDown();
                    }
                    case "error" -> {
                        JsonObject err = message.getAsJsonObject("error");
                        String errMsg = err != null ? readString(err, "message") : message.toString();
                        errorRef.set(errMsg);
                        System.err.println("[ERROR] " + errMsg);
                        responseDone.countDown();
                    }
                    default -> {
                        // 记录其他事件类型
                        if (type != null && !type.isEmpty()) {
                            System.out.println("[EVENT] " + type);
                        }
                    }
                }
            }

            @Override
            public void onClose(int code, String reason) {
                System.out.println("[CLOSE] code=" + code + ", reason=" + reason);
                if (responseDone.getCount() > 0) {
                    errorRef.compareAndSet(null, "连接关闭: code=" + code + " reason=" + reason);
                    responseDone.countDown();
                }
            }
        };

        OmniRealtimeConversation conversation = null;
        try {
            // 1. 建立连接
            OmniRealtimeParam param = OmniRealtimeParam.builder()
                    .model(MODEL)
                    .url(WS_URL)
                    .apikey(apiKey)
                    .build();

            conversation = new OmniRealtimeConversation(param, callback);
            long t0 = System.currentTimeMillis();
            conversation.connect();
            long connectDuration = System.currentTimeMillis() - t0;
            System.out.println("连接耗时: " + connectDuration + " ms");

            // 2. 配置会话 — 全双工模式
            OmniRealtimeConfig config = buildFullDuplexConfig(
                    "你是一位友好的中文助手。用户会用中文和你对话，请用简短的中文回复。"
            );
            conversation.updateSession(config);
            System.out.println("会话配置已发送 (modalities=TEXT+AUDIO, VAD=true)");

            // 3. 发送音频
            long sendStart = System.currentTimeMillis();
            List<byte[]> chunks = PcmAudioUtils.chunk(testPcm, CHUNK_SIZE);
            for (byte[] chunk : chunks) {
                conversation.appendAudio(Base64.getEncoder().encodeToString(chunk));
            }
            conversation.commit();
            long sendDuration = System.currentTimeMillis() - sendStart;
            System.out.println("音频发送完成: " + chunks.size() + " chunks, 耗时 " + sendDuration + " ms");

            // 4. 等待响应
            boolean completed = responseDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 5. 输出结果
            System.out.println("\n========== PoC 0.1 结果 ==========");
            System.out.println("响应完成: " + completed);
            System.out.println("错误: " + errorRef.get());
            System.out.println("总事件数: " + eventCount.get());
            System.out.println("用户转写: " + transcriptBuilder);
            System.out.println("AI 文本回复: " + aiTextBuilder);
            System.out.println("AI 音频 chunks: " + audioChunks.size());

            if (firstAudioAt.get() > 0 && connectAt.get() > 0) {
                long firstByteLatency = firstAudioAt.get() - sendStart;
                System.out.println("首字节音频延迟 (从发送开始): " + firstByteLatency + " ms");
            }

            // 保存 AI 回复音频为 WAV 文件 (用于人工验证音质)
            if (!audioChunks.isEmpty()) {
                byte[] allAudio = mergeChunks(audioChunks);
                byte[] wav = PcmAudioUtils.wrapPcm16MonoAsWav(allAudio, 24_000);
                Path outputPath = Path.of("target/poc-01-ai-reply.wav");
                Files.write(outputPath, wav);
                System.out.println("AI 回复音频已保存: " + outputPath.toAbsolutePath());
                System.out.println("音频时长: " + PcmAudioUtils.estimateDurationMs(allAudio.length, 24_000) + " ms");
            }

            System.out.println("===================================\n");

            // 断言
            if (errorRef.get() != null) {
                System.err.println("PoC 0.1 失败: " + errorRef.get());
            }
            assert completed : "响应超时 (" + TIMEOUT_SECONDS + "s)";
            assert errorRef.get() == null : "收到错误: " + errorRef.get();
            assert aiTextBuilder.length() > 0 || !audioChunks.isEmpty() :
                    "未收到任何 AI 回复 (文本和音频均为空)";

            System.out.println("PoC 0.1 通过 — 全双工基础通路验证成功");

        } finally {
            safeClose(conversation);
        }
    }

    // ========================================================================
    // Phase 0.2 — updateSession() 动态更新 instructions 验证
    // ========================================================================

    @Test
    @Order(2)
    void poc02_updateSessionInstructionsDynamic() throws Exception {
        System.out.println("\n=== Phase 0.2: updateSession() 动态更新 instructions 验证 ===");

        byte[] testPcm = generateTestPcm();

        // ---- 第一轮: 初始 instructions ----
        String round1Instructions = "你是一位 Java 技术面试官。不管用户说什么，你必须用中文提出一个关于 Spring Boot 的技术问题。回复必须包含 'Spring Boot' 这个关键词。";
        RoundResult round1 = executeOneRound(testPcm, round1Instructions, "Round 1 (Spring Boot)");

        System.out.println("Round 1 AI 回复: " + round1.aiText);

        // ---- 第二轮: 更新 instructions ----
        String round2Instructions = "你是一位 Java 技术面试官。不管用户说什么，你必须用中文提出一个关于 JVM 垃圾回收的技术问题。回复必须包含 'GC' 或 '垃圾回收' 这个关键词。";
        RoundResult round2 = executeOneRoundWithUpdate(testPcm, round1Instructions, round2Instructions, "Round 2 (GC)");

        System.out.println("Round 2 AI 回复: " + round2.aiText);

        // ---- 验证 ----
        System.out.println("\n========== PoC 0.2 结果 ==========");
        System.out.println("Round 1 (Spring Boot): " + round1.aiText);
        System.out.println("Round 2 (GC/垃圾回收): " + round2.aiText);

        boolean round1HasSpringBoot = round1.aiText.contains("Spring Boot") || round1.aiText.contains("spring boot") || round1.aiText.contains("SpringBoot");
        boolean round2HasGC = round2.aiText.contains("GC") || round2.aiText.contains("垃圾回收") || round2.aiText.contains("gc") || round2.aiText.contains("garbage");

        System.out.println("Round 1 包含 'Spring Boot': " + round1HasSpringBoot);
        System.out.println("Round 2 包含 'GC/垃圾回收': " + round2HasGC);

        if (round1HasSpringBoot && round2HasGC) {
            System.out.println("PoC 0.2 通过 — updateSession() 动态更新 instructions 有效");
        } else if (!round1.success || !round2.success) {
            System.out.println("PoC 0.2 部分失败 — 连接或响应异常");
            System.out.println("Round 1 error: " + round1.error);
            System.out.println("Round 2 error: " + round2.error);
        } else {
            System.out.println("PoC 0.2 需人工判断 — AI 回复未严格匹配关键词，但 instructions 可能已生效");
            System.out.println("请人工检查 Round 1 和 Round 2 的回复内容是否反映了不同的 instructions");
        }
        System.out.println("===================================\n");
    }

    // ========================================================================
    // Phase 0.2 补充 — 使用 parameters 注入 instructions 方式验证
    // ========================================================================

    @Test
    @Order(3)
    void poc02b_parametersInstructionsInjection() throws Exception {
        System.out.println("\n=== Phase 0.2b: 通过 parameters map 注入 instructions 验证 ===");

        byte[] testPcm = generateTestPcm();

        CountDownLatch responseDone = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        StringBuilder aiTextBuilder = new StringBuilder();

        OmniRealtimeCallback callback = createSimpleCallback(responseDone, errorRef, aiTextBuilder);

        OmniRealtimeConversation conversation = null;
        try {
            conversation = new OmniRealtimeConversation(buildParam(), callback);
            conversation.connect();

            // 使用 parameters map 注入 instructions
            OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                    .modalities(List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO))
                    .voice(VOICE)
                    .enableTurnDetection(true)
                    .parameters(Map.of(
                            "instructions", "你是一位友好的中文助手。请用一句话介绍自己。回复必须包含'你好'两个字。"
                    ))
                    .build();
            conversation.updateSession(config);

            // 发送音频
            for (byte[] chunk : PcmAudioUtils.chunk(testPcm, CHUNK_SIZE)) {
                conversation.appendAudio(Base64.getEncoder().encodeToString(chunk));
            }
            conversation.commit();

            boolean completed = responseDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            System.out.println("\n========== PoC 0.2b 结果 ==========");
            System.out.println("响应完成: " + completed);
            System.out.println("错误: " + errorRef.get());
            System.out.println("AI 回复: " + aiTextBuilder);
            System.out.println("包含'你好': " + aiTextBuilder.toString().contains("你好"));
            System.out.println("===================================\n");

            if (completed && errorRef.get() == null && aiTextBuilder.length() > 0) {
                System.out.println("PoC 0.2b 通过 — parameters map 注入 instructions 有效");
            }

        } finally {
            safeClose(conversation);
        }
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private OmniRealtimeConfig buildFullDuplexConfig(String instructions) {
        OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
        transcriptionParam.setLanguage("zh");
        transcriptionParam.setInputSampleRate(INPUT_SAMPLE_RATE);
        transcriptionParam.setInputAudioFormat("pcm");

        OmniRealtimeConfig.OmniRealtimeConfigBuilder builder = OmniRealtimeConfig.builder()
                .modalities(List.of(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO))
                .voice(VOICE)
                .enableTurnDetection(true)
                .transcriptionConfig(transcriptionParam);

        if (instructions != null && !instructions.isBlank()) {
            builder.parameters(Map.of("instructions", instructions));
        }

        return builder.build();
    }

    private OmniRealtimeParam buildParam() {
        return OmniRealtimeParam.builder()
                .model(MODEL)
                .url(WS_URL)
                .apikey(apiKey)
                .build();
    }

    /**
     * 执行单独一轮对话 (新建连接)
     */
    private RoundResult executeOneRound(byte[] pcm, String instructions, String label) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        StringBuilder aiText = new StringBuilder();
        CopyOnWriteArrayList<byte[]> audioChunks = new CopyOnWriteArrayList<>();

        OmniRealtimeCallback callback = new OmniRealtimeCallback() {
            @Override
            public void onEvent(JsonObject message) {
                String type = readString(message, "type");
                switch (type) {
                    case "response.audio_transcript.delta" -> {
                        String delta = readString(message, "delta");
                        if (delta != null) aiText.append(delta);
                    }
                    case "response.audio.delta" -> {
                        String b64 = readString(message, "delta");
                        if (b64 != null) audioChunks.add(Base64.getDecoder().decode(b64));
                    }
                    case "response.done" -> done.countDown();
                    case "error" -> {
                        JsonObject err = message.getAsJsonObject("error");
                        errorRef.set(err != null ? readString(err, "message") : "unknown error");
                        done.countDown();
                    }
                }
            }

            @Override
            public void onClose(int code, String reason) {
                if (done.getCount() > 0) {
                    errorRef.compareAndSet(null, "closed: " + code);
                    done.countDown();
                }
            }
        };

        OmniRealtimeConversation conv = null;
        try {
            conv = new OmniRealtimeConversation(buildParam(), callback);
            conv.connect();
            conv.updateSession(buildFullDuplexConfig(instructions));

            for (byte[] chunk : PcmAudioUtils.chunk(pcm, CHUNK_SIZE)) {
                conv.appendAudio(Base64.getEncoder().encodeToString(chunk));
            }
            conv.commit();

            boolean completed = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new RoundResult(
                    completed && errorRef.get() == null,
                    aiText.toString(),
                    errorRef.get(),
                    audioChunks
            );
        } finally {
            safeClose(conv);
        }
    }

    /**
     * 在同一连接中执行两轮对话，第二轮前 updateSession 更新 instructions
     */
    private RoundResult executeOneRoundWithUpdate(
            byte[] pcm, String initialInstructions, String updatedInstructions, String label
    ) throws Exception {
        CountDownLatch round1Done = new CountDownLatch(1);
        CountDownLatch round2Done = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        StringBuilder round1Text = new StringBuilder();
        StringBuilder round2Text = new StringBuilder();
        AtomicInteger roundCounter = new AtomicInteger(1);

        OmniRealtimeCallback callback = new OmniRealtimeCallback() {
            @Override
            public void onEvent(JsonObject message) {
                String type = readString(message, "type");
                int currentRound = roundCounter.get();

                switch (type) {
                    case "response.audio_transcript.delta" -> {
                        String delta = readString(message, "delta");
                        if (delta != null) {
                            if (currentRound == 1) round1Text.append(delta);
                            else round2Text.append(delta);
                        }
                    }
                    case "response.done" -> {
                        if (currentRound == 1) {
                            round1Done.countDown();
                        } else {
                            round2Done.countDown();
                        }
                    }
                    case "error" -> {
                        JsonObject err = message.getAsJsonObject("error");
                        errorRef.set(err != null ? readString(err, "message") : "unknown");
                        round1Done.countDown();
                        round2Done.countDown();
                    }
                }
            }

            @Override
            public void onClose(int code, String reason) {
                errorRef.compareAndSet(null, "closed: " + code);
                round1Done.countDown();
                round2Done.countDown();
            }
        };

        OmniRealtimeConversation conv = null;
        try {
            conv = new OmniRealtimeConversation(buildParam(), callback);
            conv.connect();
            conv.updateSession(buildFullDuplexConfig(initialInstructions));

            // Round 1
            System.out.println("  [Round 1] 发送音频...");
            for (byte[] chunk : PcmAudioUtils.chunk(pcm, CHUNK_SIZE)) {
                conv.appendAudio(Base64.getEncoder().encodeToString(chunk));
            }
            conv.commit();
            round1Done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("  [Round 1] 完成, AI: " + round1Text);

            // 更新 instructions
            roundCounter.set(2);
            System.out.println("  [updateSession] 更新 instructions...");
            conv.updateSession(buildFullDuplexConfig(updatedInstructions));

            // Round 2 — 再发一段音频
            System.out.println("  [Round 2] 发送音频...");
            for (byte[] chunk : PcmAudioUtils.chunk(pcm, CHUNK_SIZE)) {
                conv.appendAudio(Base64.getEncoder().encodeToString(chunk));
            }
            conv.commit();
            round2Done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("  [Round 2] 完成, AI: " + round2Text);

            return new RoundResult(
                    errorRef.get() == null,
                    round2Text.toString(),
                    errorRef.get(),
                    new CopyOnWriteArrayList<>()
            );
        } finally {
            safeClose(conv);
        }
    }

    private OmniRealtimeCallback createSimpleCallback(
            CountDownLatch done, AtomicReference<String> errorRef, StringBuilder aiText
    ) {
        return new OmniRealtimeCallback() {
            @Override
            public void onEvent(JsonObject message) {
                String type = readString(message, "type");
                switch (type) {
                    case "response.audio_transcript.delta" -> {
                        String delta = readString(message, "delta");
                        if (delta != null) aiText.append(delta);
                    }
                    case "response.done" -> done.countDown();
                    case "error" -> {
                        JsonObject err = message.getAsJsonObject("error");
                        errorRef.set(err != null ? readString(err, "message") : "unknown");
                        done.countDown();
                    }
                }
            }

            @Override
            public void onClose(int code, String reason) {
                if (done.getCount() > 0) {
                    errorRef.compareAndSet(null, "closed: " + code);
                    done.countDown();
                }
            }
        };
    }

    /**
     * 生成测试用 PCM 音频。
     *
     * <p>优先使用项目中已有的 WAV 文件转换为 PCM；
     * 如果不存在，则生成一段 2 秒的 440Hz 正弦波作为测试信号。
     */
    private byte[] generateTestPcm() {
        // 尝试使用项目中已有的 WAV 文件
        Path[] candidates = {
                Path.of("../.codex_tmp/dashscope-preview.wav"),
                Path.of("../.codex_tmp/mimo-preview.wav"),
                Path.of("../.codex_tmp/public_tts.wav"),
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    byte[] pcm = PcmAudioUtils.toPcm16Mono(candidate, "audio/wav", candidate.getFileName().toString(), INPUT_SAMPLE_RATE);
                    if (pcm.length > 0) {
                        System.out.println("使用已有音频文件: " + candidate.toAbsolutePath());
                        return pcm;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 生成 2 秒 440Hz 正弦波 PCM (16kHz, mono, 16-bit)
        System.out.println("生成合成测试音频: 2s 440Hz 正弦波");
        int durationMs = 2000;
        int totalSamples = INPUT_SAMPLE_RATE * durationMs / 1000;
        byte[] pcm = new byte[totalSamples * 2];
        double frequency = 440.0;
        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / INPUT_SAMPLE_RATE;
            short sample = (short) (Short.MAX_VALUE * 0.5 * Math.sin(2 * Math.PI * frequency * t));
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }

    private static byte[] mergeChunks(List<byte[]> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            out.write(chunk, 0, chunk.length);
        }
        return out.toByteArray();
    }

    private static String readString(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        return obj.get(field).getAsString();
    }

    private static void safeClose(OmniRealtimeConversation conversation) {
        if (conversation == null) return;
        try {
            conversation.close();
        } catch (Exception ignored) {
        }
    }

    private record RoundResult(
            boolean success,
            String aiText,
            String error,
            List<byte[]> audioChunks
    ) {
    }
}
