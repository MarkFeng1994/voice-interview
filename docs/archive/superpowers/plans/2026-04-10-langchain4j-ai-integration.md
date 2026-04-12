# LangChain4j AI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current hand-written LLM orchestration path with a LangChain4j-backed `AiService` implementation while preserving the existing REST/WebSocket behavior and keeping ASR/TTS providers unchanged.

**Architecture:** Keep the existing `AiService` abstraction and provider switch, but evolve its interview method from a raw string input to a structured command so the LLM layer receives question/stage/follow-up context. Add a new `langchain4j` AI provider that uses LangChain4j `AiServices` + structured output POJOs against the current OpenAI-compatible endpoint, and leave `mock` and `openai` implementations available as fallbacks. Centralize assistant creation in one factory so future tools, chat memory, RAG, and agent workflows hang off a single seam.

**Tech Stack:** Java 21, Spring Boot 3.5.x, LangChain4j core + OpenAI integration, Spring Web, MyBatis-Plus, JUnit 5, AssertJ.

---

## File Structure

**Create**
- `voice-interview-backend/src/main/java/com/interview/common/config/LangChain4jAiProperties.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReplyCommand.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReplyAssistant.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeKeywordAssistant.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionAssistant.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReplyOutput.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeKeywordOutput.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionListOutput.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionOutput.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`
- `voice-interview-backend/src/test/java/com/interview/module/system/service/ProviderRuntimeStatusServiceTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`
- `voice-interview-backend/src/main/resources/application-langchain4j.properties`

**Modify**
- `voice-interview-backend/pom.xml`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/MockAiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/controller/InterviewController.java`
- `voice-interview-backend/src/main/java/com/interview/module/system/service/ProviderRuntimeStatusService.java`
- `voice-interview-backend/src/main/resources/application.properties`
- `voice-interview-backend/src/main/resources/application-openai.properties`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`
- `ops/smoke-check.ps1`
- `README.md`

**Do Not Change In This Plan**
- `voice-interview-mobile/**`
- `voice-interview-admin/**`
- `voice-interview-backend/src/main/java/com/interview/module/asr/**`
- `voice-interview-backend/src/main/java/com/interview/module/tts/**`

---

### Task 1: Add LangChain4j Dependencies And Provider Configuration

**Files:**
- Modify: `voice-interview-backend/pom.xml`
- Create: `voice-interview-backend/src/main/java/com/interview/common/config/LangChain4jAiProperties.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/system/service/ProviderRuntimeStatusService.java`
- Create: `voice-interview-backend/src/main/resources/application-langchain4j.properties`
- Modify: `voice-interview-backend/src/main/resources/application.properties`
- Test: `voice-interview-backend/src/test/java/com/interview/module/system/service/ProviderRuntimeStatusServiceTest.java`

- [ ] **Step 1: Write the failing provider runtime test**

```java
package com.interview.module.system.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.interview.common.config.DashScopeProperties;
import com.interview.common.config.OpenAiProperties;

class ProviderRuntimeStatusServiceTest {

    @Test
    void should_report_langchain4j_ai_provider_as_configured_when_openai_endpoint_exists() {
        OpenAiProperties openAi = new OpenAiProperties();
        openAi.getAi().setBaseUrl("https://icoe.pp.ua/v1");
        openAi.getAi().setApiKey("test-key");
        openAi.getAi().setModel("gpt-5.4-xhigh-px");

        ProviderRuntimeStatusService service = new ProviderRuntimeStatusService(
                "langchain4j",
                "mock",
                "mock",
                openAi,
                new DashScopeProperties()
        );

        ProviderRuntimeStatusService.ProviderRuntimePayload payload = service.inspect();

        assertThat(payload.ai().provider()).isEqualTo("langchain4j");
        assertThat(payload.ai().status()).isEqualTo("CONFIGURED");
        assertThat(payload.ai().details()).containsEntry("baseUrl", "https://icoe.pp.ua/v1");
        assertThat(payload.ai().details()).containsEntry("model", "gpt-5.4-xhigh-px");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q "-Dtest=ProviderRuntimeStatusServiceTest" test
```

Expected: FAIL because `ProviderRuntimeStatusService` does not yet recognize `app.ai.provider=langchain4j`.

- [ ] **Step 3: Add LangChain4j dependencies and configuration classes**

Update `voice-interview-backend/pom.xml` with explicit LangChain4j dependencies:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.12.2-beta22</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.12.2-beta22</version>
</dependency>
```

Create `voice-interview-backend/src/main/java/com/interview/common/config/LangChain4jAiProperties.java`:

```java
package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.langchain4j.ai")
public class LangChain4jAiProperties {

    private double temperature = 0.2;
    private int timeoutSeconds = 60;
    private boolean strictJsonSchema = true;
    private int maxRetries = 1;

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isStrictJsonSchema() {
        return strictJsonSchema;
    }

    public void setStrictJsonSchema(boolean strictJsonSchema) {
        this.strictJsonSchema = strictJsonSchema;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
```

Update `voice-interview-backend/src/main/resources/application.properties` with a commented provider option:

```properties
# app.ai.provider=langchain4j
```

Create `voice-interview-backend/src/main/resources/application-langchain4j.properties`:

```properties
app.ai.provider=langchain4j
app.langchain4j.ai.temperature=0.2
app.langchain4j.ai.timeout-seconds=60
app.langchain4j.ai.strict-json-schema=true
app.langchain4j.ai.max-retries=1
```

Update `ProviderRuntimeStatusService.inspectAi()` to support the new provider:

```java
if ("langchain4j".equalsIgnoreCase(aiProvider)) {
    String baseUrl = openAiProperties.resolveAiBaseUrl();
    String apiKey = openAiProperties.resolveAiApiKey();
    String model = openAiProperties.resolveAiModel();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("baseUrl", baseUrl);
    details.put("model", model);
    details.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank());
    if (apiKey == null || apiKey.isBlank()) {
        return new CapabilityStatus("langchain4j", "DOWN", "LangChain4j LLM missing API Key", details);
    }
    return new CapabilityStatus("langchain4j", "CONFIGURED", "LangChain4j LLM configured", details);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -q "-Dtest=ProviderRuntimeStatusServiceTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/pom.xml voice-interview-backend/src/main/java/com/interview/common/config/LangChain4jAiProperties.java voice-interview-backend/src/main/java/com/interview/module/system/service/ProviderRuntimeStatusService.java voice-interview-backend/src/main/resources/application.properties voice-interview-backend/src/main/resources/application-langchain4j.properties voice-interview-backend/src/test/java/com/interview/module/system/service/ProviderRuntimeStatusServiceTest.java
git commit -m "feat: add langchain4j provider configuration"
```

### Task 2: Upgrade AiService To Structured Interview Commands

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReplyCommand.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/controller/InterviewController.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

- [ ] **Step 1: Write the failing integration test for structured command propagation**

Add this test and spy inside `SimpleInterviewEngineIntegrationTest`:

```java
@Test
void should_pass_question_context_into_ai_service_command() {
    CapturingAiService aiService = new CapturingAiService();
    SimpleInterviewEngine engine = engineWith(aiService);
    InterviewSessionView view = engine.startSession(
            List.of(new InterviewQuestionCard("Redis 场景", "请讲一下你在项目里如何使用 Redis。")),
            60,
            2,
            new InterviewSessionOwner("1", "tester"),
            null,
            null
    );

    engine.answer(view.sessionId(), "1", "TEXT", "我主要用 Redis 做缓存和分布式锁。", null);

    assertThat(aiService.lastCommand.question()).isEqualTo("请讲一下你在项目里如何使用 Redis。");
    assertThat(aiService.lastCommand.answer()).isEqualTo("我主要用 Redis 做缓存和分布式锁。");
    assertThat(aiService.lastCommand.stage()).isEqualTo("OPENING");
    assertThat(aiService.lastCommand.followUpIndex()).isEqualTo(0);
}

private static final class CapturingAiService implements AiService {
    private InterviewReplyCommand lastCommand;

    @Override
    public AiReply generateInterviewReply(InterviewReplyCommand command) {
        this.lastCommand = command;
        return new AiReply("继续", "NEXT_QUESTION", 80);
    }

    @Override
    public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
        return new ResumeKeywordExtractionResult("summary", List.of(), List.of());
    }

    @Override
    public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
        return List.of();
    }
}

private SimpleInterviewEngine engineWith(AiService aiService) {
    InterviewSessionStore sessionStore = new InMemorySessionStore();
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
            Map.of("reportStore", new NoopInterviewReportStore())
    );
    return new SimpleInterviewEngine(
            sessionStore,
            beanFactory.getBeanProvider(InterviewReportStore.class),
            aiService,
            new StubTtsService()
    );
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_pass_question_context_into_ai_service_command" test
```

Expected: FAIL because `AiService` still accepts `String inputText`.

- [ ] **Step 3: Introduce the command record and update callers**

Create `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReplyCommand.java`:

```java
package com.interview.module.ai.service;

import java.util.List;

public record InterviewReplyCommand(
        String question,
        String answer,
        String stage,
        int followUpIndex,
        int maxFollowUpPerQuestion,
        List<String> expectedPoints
) {

    public InterviewReplyCommand {
        expectedPoints = expectedPoints == null ? List.of() : List.copyOf(expectedPoints);
    }
}
```

Update `AiService.java`:

```java
AiReply generateInterviewReply(InterviewReplyCommand command);
```

Update `SimpleInterviewEngine.answer(...)`:

```java
AiReply aiReply = aiService.generateInterviewReply(new InterviewReplyCommand(
        currentQuestion.promptSnapshot(),
        normalizedText,
        sessionState.getStage(),
        sessionState.getFollowUpIndex(),
        sessionState.getMaxFollowUpPerQuestion(),
        expectedPoints(currentQuestion)
));
```

Update `InterviewController.replyPreview(...)`:

```java
AiReply aiReply = aiService.generateInterviewReply(new InterviewReplyCommand(
        null,
        request.inputText(),
        "PREVIEW",
        0,
        0,
        java.util.List.of()
));
```

Update all `AiService` implementations to compile by accepting `InterviewReplyCommand`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_pass_question_context_into_ai_service_command" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReplyCommand.java voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java voice-interview-backend/src/main/java/com/interview/module/interview/controller/InterviewController.java voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java voice-interview-backend/src/main/java/com/interview/module/ai/service/MockAiService.java voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java
git commit -m "refactor: pass structured interview context into ai service"
```

### Task 3: Implement LangChain4j Assistants And AiService

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReplyOutput.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeKeywordOutput.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionOutput.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionListOutput.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReplyAssistant.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeKeywordAssistant.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/ResumeQuestionAssistant.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`

- [ ] **Step 1: Write the failing LangChain4j service test**

Create `LangChain4jAiServiceTest.java`:

```java
package com.interview.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.interview.module.ai.service.langchain4j.InterviewReplyOutput;
import com.interview.module.ai.service.langchain4j.LangChain4jAiService;
import com.interview.module.ai.service.langchain4j.ResumeKeywordOutput;
import com.interview.module.ai.service.langchain4j.ResumeQuestionListOutput;
import com.interview.module.ai.service.langchain4j.ResumeQuestionOutput;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.system.service.ProviderMetricsService;

class LangChain4jAiServiceTest {

    @Test
    void should_map_structured_langchain4j_output_into_ai_reply() {
        LangChain4jAiService service = new LangChain4jAiService(
                command -> new InterviewReplyOutput("请继续说明你的限流策略。", "FOLLOW_UP", 86),
                text -> new ResumeKeywordOutput("五年 Java 后端经验", List.of("Java", "Redis"), List.of("负责交易链路治理")),
                command -> new ResumeQuestionListOutput(List.of(new ResumeQuestionOutput("限流设计", "讲一下你如何做限流。", "Redis", 2))),
                new ProviderMetricsService()
        );

        AiReply reply = service.generateInterviewReply(new InterviewReplyCommand(
                "请讲一下你的 Redis 使用场景",
                "我主要用 Redis 做缓存和分布式锁",
                "JAVA_CORE",
                0,
                2,
                List.of("缓存", "分布式锁")
        ));

        assertThat(reply.spokenText()).isEqualTo("请继续说明你的限流策略。");
        assertThat(reply.decisionSuggestion()).isEqualTo("FOLLOW_UP");
        assertThat(reply.scoreSuggestion()).isEqualTo(86);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q "-Dtest=LangChain4jAiServiceTest" test
```

Expected: FAIL because `LangChain4jAiService` and its assistant/output types do not exist.

- [ ] **Step 3: Implement LangChain4j assistants, outputs, and service**

Create `InterviewReplyOutput.java`:

```java
package com.interview.module.ai.service.langchain4j;

public record InterviewReplyOutput(
        String spokenText,
        String decisionSuggestion,
        Integer scoreSuggestion
) {
}
```

Create `ResumeKeywordOutput.java`:

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

public record ResumeKeywordOutput(
        String summary,
        List<String> keywords,
        List<String> experienceHighlights
) {
}
```

Create `ResumeQuestionOutput.java` and `ResumeQuestionListOutput.java`:

```java
package com.interview.module.ai.service.langchain4j;

public record ResumeQuestionOutput(
        String title,
        String prompt,
        String targetKeyword,
        int difficulty
) {
}
```

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

public record ResumeQuestionListOutput(List<ResumeQuestionOutput> questions) {
}
```

Create assistant interfaces:

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewReplyAssistant {

    @SystemMessage("""
            You are a professional technical interviewer.
            Return only a JSON object matching the InterviewReplyOutput schema.
            Prefer FOLLOW_UP only when the answer is incomplete and more detail is useful.
            """)
    @UserMessage("""
            Current question: {{question}}
            Candidate answer: {{answer}}
            Interview stage: {{stage}}
            Follow-up index: {{followUpIndex}} / {{maxFollowUpPerQuestion}}
            Expected points: {{expectedPoints}}
            """)
    InterviewReplyOutput reply(
            @V("question") String question,
            @V("answer") String answer,
            @V("stage") String stage,
            @V("followUpIndex") int followUpIndex,
            @V("maxFollowUpPerQuestion") int maxFollowUpPerQuestion,
            @V("expectedPoints") List<String> expectedPoints
    );
}
```

```java
package com.interview.module.ai.service.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ResumeKeywordAssistant {

    @SystemMessage("""
            你是一个技术简历分析专家。
            返回符合 ResumeKeywordOutput 的 JSON。
            """)
    @UserMessage("简历正文：{{it}}")
    ResumeKeywordOutput extract(String resumeText);
}
```

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResumeQuestionAssistant {

    @SystemMessage("""
            你是一个技术面试出题专家。
            返回符合 ResumeQuestionListOutput 的 JSON。
            """)
    @UserMessage("""
            候选人概况：{{resumeSummary}}
            需要覆盖的关键词：{{missingKeywords}}
            已有题目：{{existingQuestionTitles}}
            生成题目数量：{{questionCount}}
            """)
    ResumeQuestionListOutput generate(
            @V("resumeSummary") String resumeSummary,
            @V("missingKeywords") List<String> missingKeywords,
            @V("existingQuestionTitles") List<String> existingQuestionTitles,
            @V("questionCount") int questionCount
    );
}
```

Create `LangChain4jAssistantFactory.java`:

```java
package com.interview.module.ai.service.langchain4j;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import java.time.Duration;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.interview.common.config.LangChain4jAiProperties;
import com.interview.common.config.OpenAiProperties;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

@Configuration
public class LangChain4jAssistantFactory {

    @Bean
    ChatModel langChain4jChatModel(OpenAiProperties openAiProperties, LangChain4jAiProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(openAiProperties.resolveAiBaseUrl())
                .apiKey(openAiProperties.resolveAiApiKey())
                .modelName(openAiProperties.resolveAiModel())
                .temperature(properties.getTemperature())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(properties.getMaxRetries())
                .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                .strictJsonSchema(properties.isStrictJsonSchema())
                .build();
    }

    @Bean
    InterviewReplyAssistant interviewReplyAssistant(ChatModel chatModel) {
        return AiServices.create(InterviewReplyAssistant.class, chatModel);
    }

    @Bean
    ResumeKeywordAssistant resumeKeywordAssistant(ChatModel chatModel) {
        return AiServices.create(ResumeKeywordAssistant.class, chatModel);
    }

    @Bean
    ResumeQuestionAssistant resumeQuestionAssistant(ChatModel chatModel) {
        return AiServices.create(ResumeQuestionAssistant.class, chatModel);
    }
}
```

Create `LangChain4jAiService.java`:

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReplyCommand;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;
import com.interview.module.interview.service.InterviewAnswerAnalyzer;
import com.interview.module.system.service.ProviderMetricsService;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "langchain4j")
public class LangChain4jAiService implements AiService {

    private final InterviewReplyAssistant interviewReplyAssistant;
    private final ResumeKeywordAssistant resumeKeywordAssistant;
    private final ResumeQuestionAssistant resumeQuestionAssistant;
    private final ProviderMetricsService providerMetricsService;

    public LangChain4jAiService(
            InterviewReplyAssistant interviewReplyAssistant,
            ResumeKeywordAssistant resumeKeywordAssistant,
            ResumeQuestionAssistant resumeQuestionAssistant,
            ProviderMetricsService providerMetricsService
    ) {
        this.interviewReplyAssistant = interviewReplyAssistant;
        this.resumeKeywordAssistant = resumeKeywordAssistant;
        this.resumeQuestionAssistant = resumeQuestionAssistant;
        this.providerMetricsService = providerMetricsService;
    }

    @Override
    public AiReply generateInterviewReply(InterviewReplyCommand command) {
        return providerMetricsService.record("AI", "langchain4j", () -> {
            InterviewReplyOutput output = interviewReplyAssistant.reply(
                    command.question(),
                    command.answer(),
                    command.stage(),
                    command.followUpIndex(),
                    command.maxFollowUpPerQuestion(),
                    command.expectedPoints()
            );
            return new AiReply(output.spokenText(), output.decisionSuggestion(), output.scoreSuggestion());
        });
    }

    @Override
    public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
        return providerMetricsService.record("AI_RESUME_KEYWORDS", "langchain4j", () -> {
            ResumeKeywordOutput output = resumeKeywordAssistant.extract(resumeText);
            return new ResumeKeywordExtractionResult(output.summary(), output.keywords(), output.experienceHighlights());
        });
    }

    @Override
    public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
        return providerMetricsService.record("AI_RESUME_QUESTIONS", "langchain4j", () -> {
            ResumeQuestionListOutput output = resumeQuestionAssistant.generate(
                    command.resumeSummary(),
                    command.missingKeywords(),
                    command.existingQuestionTitles(),
                    command.questionCount()
            );
            return output.questions().stream()
                    .map(item -> new GeneratedResumeQuestion(item.title(), item.prompt(), item.targetKeyword(), item.difficulty()))
                    .limit(command.questionCount())
                    .toList();
        });
    }

    @Override
    public InterviewAnswerAnalyzer.Analysis analyzeInterviewAnswer(String question, String answer, List<String> expectedPoints) {
        return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -q "-Dtest=LangChain4jAiServiceTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java
git commit -m "feat: add langchain4j-backed ai service"
```

### Task 4: Keep Existing Providers Compatible With The New Contract

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/MockAiService.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`

- [ ] **Step 1: Write the failing legacy-provider regression update**

Update `OpenAiCompatibleAiServiceTest` call sites so they use `InterviewReplyCommand`:

```java
AiReply reply = service.generateInterviewReply(new InterviewReplyCommand(
        "请介绍一个你最熟悉的项目",
        "我负责交易链路和缓存治理",
        "PROJECT_DEEP_DIVE",
        0,
        2,
        List.of("项目职责", "技术取舍")
));
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q "-Dtest=OpenAiCompatibleAiServiceTest" test
```

Expected: FAIL until `OpenAiCompatibleAiService` and `MockAiService` implement the new method signature.

- [ ] **Step 3: Adapt the existing providers without changing behavior**

Update `MockAiService.java`:

```java
@Override
public AiReply generateInterviewReply(InterviewReplyCommand command) {
    return new AiReply("好的，我们继续下一题。", "NEXT_QUESTION", 80);
}
```

Update `OpenAiCompatibleAiService.java`:

```java
@Override
public AiReply generateInterviewReply(InterviewReplyCommand command) {
    return providerMetricsService.record("AI", "openai", () -> {
        requireApiKey();
        String content = invokeTextCompletion(buildInterviewSystemPrompt(command), buildInterviewUserContent(command), true);
        try {
            JsonNode contentJson = objectMapper.readTree(content);
            String spokenText = contentJson.path("spokenText").asText(content);
            String decisionSuggestion = contentJson.path("decisionSuggestion").asText("FOLLOW_UP");
            Integer scoreSuggestion = contentJson.path("scoreSuggestion").isNumber()
                    ? contentJson.path("scoreSuggestion").asInt()
                    : null;
            return new AiReply(spokenText, decisionSuggestion, scoreSuggestion);
        } catch (Exception ex) {
            return new AiReply(content, "FOLLOW_UP", null);
        }
    });
}

private String buildInterviewSystemPrompt(InterviewReplyCommand command) {
    return SYSTEM_PROMPT + "\nUse the question, stage, and expected points to make your decision suggestion.";
}

private String buildInterviewUserContent(InterviewReplyCommand command) {
    return "Current question: " + nullToBlank(command.question())
            + "\nCandidate answer: " + nullToBlank(command.answer())
            + "\nInterview stage: " + nullToBlank(command.stage())
            + "\nFollow-up index: " + command.followUpIndex() + "/" + command.maxFollowUpPerQuestion()
            + "\nExpected points: " + String.join(", ", command.expectedPoints());
}

private String nullToBlank(String value) {
    return value == null ? "" : value;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -q "-Dtest=OpenAiCompatibleAiServiceTest,SimpleInterviewEngineIntegrationTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java voice-interview-backend/src/main/java/com/interview/module/ai/service/MockAiService.java voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "refactor: align legacy ai providers with structured command api"
```

### Task 5: Switch Smoke Coverage To LangChain4j And Document The Migration

**Files:**
- Modify: `voice-interview-backend/src/main/resources/application-openai.properties`
- Modify: `ops/smoke-check.ps1`
- Modify: `README.md`

- [ ] **Step 1: Write the failing runtime verification target**

Define the verification command up front:

```bash
mvn -q "-Dtest=ProviderRuntimeStatusServiceTest,LangChain4jAiServiceTest,OpenAiCompatibleAiServiceTest,SimpleInterviewEngineIntegrationTest" test
mvn -q -DskipTests package
./ops/smoke-check.ps1
```

Expected before wiring the profile switch: the smoke output still prints `Providers: openai/...` instead of `langchain4j/...`.

- [ ] **Step 2: Update runtime config and smoke expectations**

Change `voice-interview-backend/src/main/resources/application-openai.properties` so the AI provider line becomes:

```properties
app.ai.provider=langchain4j
```

Keep the existing `app.openai.ai.base-url`, `app.openai.ai.api-key`, and `app.openai.ai.model` lines unchanged so LangChain4j reuses the working gateway config.

Update `ops/smoke-check.ps1` only if it hard-codes provider names. The runtime print should stay generic:

```powershell
Write-Host "Providers:" ($providers.data.ai.provider + '/' + $providers.data.asr.provider + '/' + $providers.data.tts.provider)
Write-SmokePass 'provider runtime'
```

Update `README.md` with a new section:

```md
## AI Provider Layout

- `app.ai.provider=langchain4j`: LangChain4j orchestrates LLM calls on top of the OpenAI-compatible endpoint configured by `app.openai.ai.*`
- `app.ai.provider=openai`: legacy direct HTTP fallback
- `app.ai.provider=mock`: local deterministic testing

ASR and TTS providers remain independent and continue to use `app.asr.provider` and `app.tts.provider`.
```

- [ ] **Step 3: Run the verification commands**

Run:

```bash
mvn -q "-Dtest=ProviderRuntimeStatusServiceTest,LangChain4jAiServiceTest,OpenAiCompatibleAiServiceTest,SimpleInterviewEngineIntegrationTest" test
mvn -q -DskipTests package
./ops/smoke-check.ps1
```

Expected:

```text
[smoke] provider runtime PASS
Providers: langchain4j/dashscope/dashscope
Smoke check passed.
```

- [ ] **Step 4: Capture the final migration result in README**

Append a short “Why LangChain4j” note to `README.md`:

```md
## Why LangChain4j

The project keeps ASR/TTS as provider adapters but moves LLM orchestration to LangChain4j so future work can add tools, memory, RAG, and agent flows without changing public REST APIs.
```

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/resources/application-openai.properties ops/smoke-check.ps1 README.md
git commit -m "docs: switch default ai provider to langchain4j"
```

---

## Self-Review

**Spec coverage:** This plan covers provider configuration, `AiService` contract evolution, LangChain4j implementation, legacy-provider compatibility, runtime switch, smoke validation, and the agent-ready factory seam established in Task 3. It intentionally excludes ASR/TTS refactors, mobile/admin changes, and actual RAG/tool execution because those are separate subprojects.

**Placeholder scan:** No unresolved placeholders, vague “later” steps, or unspecified commands remain. Every task includes exact file paths, exact commands, and concrete code.

**Type consistency:** The plan consistently uses `InterviewReplyCommand`, `LangChain4jAiService`, `InterviewReplyAssistant`, `ResumeKeywordAssistant`, and `ResumeQuestionAssistant`. All later tasks reference the same names.
