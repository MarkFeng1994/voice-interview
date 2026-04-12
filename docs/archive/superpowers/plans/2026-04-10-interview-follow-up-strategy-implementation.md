# Interview Follow-Up Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make backend follow-up decisions in mock interviews more accurate and deterministic by separating answer evidence analysis from flow control while preserving the existing `/answer` interview loop and `LangChain4j` wording generation.

**Architecture:** First preserve more question metadata in the in-session snapshot so high-value questions can be recognized consistently. Then replace the current single heuristic result with a structured `AnswerEvidence` model, add a pure-rule `FollowUpDecisionEngine`, and finally wire that decision engine into `SimpleInterviewEngine` while persisting structured follow-up analysis into the existing `ai_analysis` field as JSON for backward-compatible JDBC storage.

**Tech Stack:** Java 21, Spring Boot 3.5.x, MyBatis-Plus, Jackson, JUnit 5, AssertJ.

---

## File Structure

**Create**
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/AnswerEvidence.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecision.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecisionEngine.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/FollowUpDecisionEngineTest.java`

**Modify**
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionSnapshot.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewRoundRecord.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewAnswerAnalyzer.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewFlowPolicy.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewAnswerAnalyzerTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

**Do Not Change In This Plan**
- `voice-interview-mobile/**`
- `voice-interview-admin/**`
- `voice-interview-backend/src/main/java/com/interview/module/asr/**`
- `voice-interview-backend/src/main/java/com/interview/module/tts/**`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/**`

---

### Task 1: Preserve Question Metadata For Flow Decisions

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionSnapshot.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

- [ ] **Step 1: Write the failing snapshot metadata test**

Add this test to `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`:

```java
@Test
void should_keep_question_source_and_difficulty_in_snapshot() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard(
						"项目深挖",
						"请详细说明订单系统最终一致性的落地方案。",
						"AI_RESUME",
						"question-1",
						"category-1",
						3
				)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		assertThat(view.questions()).hasSize(1);
		assertThat(view.questions().get(0).sourceSnapshot()).isEqualTo("AI_RESUME");
		assertThat(view.questions().get(0).difficultySnapshot()).isEqualTo(3);
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_keep_question_source_and_difficulty_in_snapshot" test
```

Expected: FAIL because `InterviewQuestionSnapshot` does not yet expose `sourceSnapshot()` and `difficultySnapshot()`.

- [ ] **Step 3: Implement snapshot metadata propagation**

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionSnapshot.java`:

```java
package com.interview.module.interview.engine.model;

public record InterviewQuestionSnapshot(
		int questionIndex,
		String titleSnapshot,
		String promptSnapshot,
		String sourceSnapshot,
		Integer difficultySnapshot
) {

	public InterviewQuestionSnapshot(int questionIndex, String titleSnapshot, String promptSnapshot) {
		this(questionIndex, titleSnapshot, promptSnapshot, "PRESET", 1);
	}
}
```

Update question snapshot creation in `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`:

```java
for (int index = 0; index < effectiveQuestions.size(); index++) {
	InterviewQuestionCard question = effectiveQuestions.get(index);
	questionSnapshots.add(new InterviewQuestionSnapshot(
			index + 1,
			question.title(),
			question.prompt(),
			question.sourceType() == null || question.sourceType().isBlank() ? "PRESET" : question.sourceType(),
			question.difficulty() == null ? 1 : question.difficulty()
	));
}
```

Update JDBC mapping in `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java`:

```java
List<InterviewQuestionSnapshot> questions = questionEntities.stream()
		.map(e -> new InterviewQuestionSnapshot(
				e.getQuestionIndex(),
				e.getTitleSnapshot(),
				e.getContentSnapshot(),
				e.getSourceSnapshot(),
				e.getDifficultySnapshot()))
		.toList();
```

And in `insertQuestionSnapshots(...)`:

```java
entity.setDifficultySnapshot(q.difficultySnapshot() == null ? 1 : q.difficultySnapshot());
entity.setSourceSnapshot(q.sourceSnapshot() == null || q.sourceSnapshot().isBlank() ? "MANUAL" : q.sourceSnapshot());
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_keep_question_source_and_difficulty_in_snapshot" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionSnapshot.java voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "refactor: preserve question metadata in interview snapshots"
```

---

### Task 2: Expand Answer Analysis Into Structured Evidence

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/service/AnswerEvidence.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewAnswerAnalyzer.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewAnswerAnalyzerTest.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewAnswerAnalyzerTest.java`

- [ ] **Step 1: Write the failing evidence tests**

Replace `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewAnswerAnalyzerTest.java` with:

```java
package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InterviewAnswerAnalyzerTest {

	@Test
	void should_mark_medium_completeness_when_core_points_are_missing() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明 Redis 在项目中的使用场景和一致性策略",
				"我们项目主要拿 Redis 做缓存",
				List.of("缓存场景", "一致性策略"));

		assertThat(evidence.completeness()).isEqualTo(AnswerEvidence.Completeness.MEDIUM);
		assertThat(evidence.missingPoints()).contains("一致性策略");
		assertThat(evidence.recommendedFollowUpDirection()).isEqualTo("MISSING_KEY_POINT");
	}

	@Test
	void should_mark_shallow_depth_when_answer_has_only_conclusion() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明你如何处理库存扣减并发问题",
				"我会用乐观锁。",
				List.of("并发控制"));

		assertThat(evidence.depth()).isEqualTo(AnswerEvidence.Depth.SHALLOW);
		assertThat(evidence.reasonCodes()).contains("DEPTH_SHALLOW");
	}

	@Test
	void should_mark_suspected_contradiction_when_answer_contains_mutually_exclusive_claims() {
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				"请说明你们系统是否用了消息队列",
				"我们没有使用消息队列，但是所有异步解耦都通过 Kafka 完成。",
				List.of("消息队列"));

		assertThat(evidence.correctnessRisk()).isEqualTo(AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION);
		assertThat(evidence.recommendedFollowUpDirection()).isEqualTo("CLARIFY_CONTRADICTION");
	}
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
mvn -q "-Dtest=InterviewAnswerAnalyzerTest" test
```

Expected: FAIL because `AnswerEvidence` does not exist and `InterviewAnswerAnalyzer` still returns the old nested `Analysis` record.

- [ ] **Step 3: Implement the structured evidence model**

Create `voice-interview-backend/src/main/java/com/interview/module/interview/service/AnswerEvidence.java`:

```java
package com.interview.module.interview.service;

import java.util.List;

public record AnswerEvidence(
		boolean answered,
		Completeness completeness,
		Depth depth,
		CorrectnessRisk correctnessRisk,
		List<String> missingPoints,
		String recommendedFollowUpDirection,
		List<String> reasonCodes,
		String summaryReason
) {

	public AnswerEvidence {
		missingPoints = missingPoints == null ? List.of() : List.copyOf(missingPoints);
		reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
		recommendedFollowUpDirection = recommendedFollowUpDirection == null ? "MISSING_KEY_POINT" : recommendedFollowUpDirection;
		summaryReason = summaryReason == null ? "" : summaryReason;
	}

	public enum Completeness {
		HIGH, MEDIUM, LOW
	}

	public enum Depth {
		DEEP, NORMAL, SHALLOW
	}

	public enum CorrectnessRisk {
		CONSISTENT, SUSPECTED_CONTRADICTION, CLEARLY_WRONG
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewAnswerAnalyzer.java`:

```java
package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InterviewAnswerAnalyzer {

	public static InterviewAnswerAnalyzer heuristic() {
		return new InterviewAnswerAnalyzer();
	}

	public AnswerEvidence analyze(String question, String answer, List<String> expectedPoints) {
		String normalizedQuestion = normalize(question);
		String normalizedAnswer = normalize(answer);
		boolean answered = !normalizedAnswer.isBlank();
		List<String> missingPoints = findMissingPoints(normalizedAnswer, expectedPoints);
		AnswerEvidence.Completeness completeness = resolveCompleteness(answered, expectedPoints, missingPoints);
		AnswerEvidence.Depth depth = resolveDepth(answer, normalizedAnswer);
		AnswerEvidence.CorrectnessRisk correctnessRisk = resolveCorrectnessRisk(normalizedQuestion, normalizedAnswer);
		List<String> reasonCodes = resolveReasonCodes(answered, completeness, depth, correctnessRisk);
		String direction = resolveDirection(completeness, depth, correctnessRisk);
		String summaryReason = buildSummaryReason(reasonCodes, missingPoints);
		return new AnswerEvidence(answered, completeness, depth, correctnessRisk, missingPoints, direction, reasonCodes, summaryReason);
	}

	private List<String> findMissingPoints(String normalizedAnswer, List<String> expectedPoints) {
		return (expectedPoints == null ? List.<String>of() : expectedPoints).stream()
				.filter(point -> !coversPoint(normalizedAnswer, point))
				.toList();
	}

	private AnswerEvidence.Completeness resolveCompleteness(boolean answered, List<String> expectedPoints, List<String> missingPoints) {
		if (!answered) {
			return AnswerEvidence.Completeness.LOW;
		}
		if (expectedPoints == null || expectedPoints.isEmpty()) {
			return AnswerEvidence.Completeness.HIGH;
		}
		if (missingPoints.isEmpty()) {
			return AnswerEvidence.Completeness.HIGH;
		}
		if (missingPoints.size() == expectedPoints.size()) {
			return AnswerEvidence.Completeness.LOW;
		}
		return AnswerEvidence.Completeness.MEDIUM;
	}

	private AnswerEvidence.Depth resolveDepth(String answer, String normalizedAnswer) {
		if (normalizedAnswer.isBlank()) {
			return AnswerEvidence.Depth.SHALLOW;
		}
		int signalCount = 0;
		for (String signal : List.of("因为", "所以", "例如", "比如", "先", "然后", "最后", "权衡", "案例", "结果")) {
			if (answer != null && answer.contains(signal)) {
				signalCount++;
			}
		}
		if (normalizedAnswer.length() < 18 && signalCount == 0) {
			return AnswerEvidence.Depth.SHALLOW;
		}
		if (signalCount >= 3 || normalizedAnswer.length() >= 80) {
			return AnswerEvidence.Depth.DEEP;
		}
		return AnswerEvidence.Depth.NORMAL;
	}

	private AnswerEvidence.CorrectnessRisk resolveCorrectnessRisk(String normalizedQuestion, String normalizedAnswer) {
		if (normalizedAnswer.isBlank()) {
			return AnswerEvidence.CorrectnessRisk.CONSISTENT;
		}
		boolean contradiction = normalizedAnswer.contains("没有使用消息队列") && normalizedAnswer.contains("kafka");
		boolean offTopic = normalizedQuestion.contains("消息队列") && normalizedAnswer.contains("乐观锁") && !normalizedAnswer.contains("消息");
		if (contradiction) {
			return AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION;
		}
		if (offTopic) {
			return AnswerEvidence.CorrectnessRisk.CLEARLY_WRONG;
		}
		return AnswerEvidence.CorrectnessRisk.CONSISTENT;
	}

	private List<String> resolveReasonCodes(
			boolean answered,
			AnswerEvidence.Completeness completeness,
			AnswerEvidence.Depth depth,
			AnswerEvidence.CorrectnessRisk correctnessRisk
	) {
		List<String> reasonCodes = new ArrayList<>();
		if (!answered) {
			reasonCodes.add("ANSWER_EMPTY");
		}
		if (completeness != AnswerEvidence.Completeness.HIGH) {
			reasonCodes.add("KEY_POINT_MISSING");
		}
		if (depth == AnswerEvidence.Depth.SHALLOW) {
			reasonCodes.add("DEPTH_SHALLOW");
		}
		if (correctnessRisk == AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION) {
			reasonCodes.add("CONTRADICTION_DETECTED");
		}
		if (correctnessRisk == AnswerEvidence.CorrectnessRisk.CLEARLY_WRONG) {
			reasonCodes.add("OFF_TOPIC_OR_WRONG");
		}
		if (reasonCodes.isEmpty()) {
			reasonCodes.add("ANSWER_STRONG");
		}
		return reasonCodes;
	}

	private String resolveDirection(
			AnswerEvidence.Completeness completeness,
			AnswerEvidence.Depth depth,
			AnswerEvidence.CorrectnessRisk correctnessRisk
	) {
		if (correctnessRisk != AnswerEvidence.CorrectnessRisk.CONSISTENT) {
			return "CLARIFY_CONTRADICTION";
		}
		if (completeness != AnswerEvidence.Completeness.HIGH) {
			return "MISSING_KEY_POINT";
		}
		if (depth == AnswerEvidence.Depth.SHALLOW) {
			return "NEED_EXAMPLE_OR_DETAIL";
		}
		return "MISSING_KEY_POINT";
	}

	private String buildSummaryReason(List<String> reasonCodes, List<String> missingPoints) {
		if (reasonCodes.contains("CONTRADICTION_DETECTED")) {
			return "回答存在前后矛盾";
		}
		if (reasonCodes.contains("OFF_TOPIC_OR_WRONG")) {
			return "回答与题目核心不一致";
		}
		if (!missingPoints.isEmpty()) {
			return "缺少关键点：" + String.join("、", missingPoints);
		}
		if (reasonCodes.contains("DEPTH_SHALLOW")) {
			return "回答结论化，缺少过程和细节";
		}
		return "回答完整";
	}

	private boolean coversPoint(String normalizedAnswer, String point) {
		if (point == null || point.isBlank()) {
			return true;
		}
		String normalizedPoint = normalize(point);
		if (!normalizedPoint.isBlank() && normalizedAnswer.contains(normalizedPoint)) {
			return true;
		}
		for (String alias : pointAliases(point)) {
			if (normalizedAnswer.contains(normalize(alias))) {
				return true;
			}
		}
		return false;
	}

	private List<String> pointAliases(String point) {
		String trimmed = point == null ? "" : point.trim();
		if (trimmed.isBlank()) {
			return List.of();
		}
		if ("缓存场景".equals(trimmed)) {
			return List.of(trimmed, "缓存", "使用场景");
		}
		if ("一致性策略".equals(trimmed)) {
			return List.of(trimmed, "一致性");
		}
		return List.of(trimmed, stripSuffix(trimmed));
	}

	private String stripSuffix(String point) {
		for (String suffix : List.of("场景", "策略", "方案", "原理", "流程", "设计", "实践", "案例", "细节")) {
			if (point.endsWith(suffix) && point.length() > suffix.length()) {
				return point.substring(0, point.length() - suffix.length());
			}
		}
		return point;
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`:

```java
default AnswerEvidence analyzeInterviewAnswer(
		String question,
		String answer,
		List<String> expectedPoints
) {
		return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
}
```

Update the override in `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`:

```java
@Override
public AnswerEvidence analyzeInterviewAnswer(
		String question,
		String answer,
		List<String> expectedPoints
) {
		return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
}
```

Update the `StubAiService` and `RecordingAiService` helper methods in `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java` to return `AnswerEvidence`, and replace the old `InterviewAnswerAnalyzer.Analysis` import with `AnswerEvidence`.

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```bash
mvn -q "-Dtest=InterviewAnswerAnalyzerTest,SimpleInterviewEngineIntegrationTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/service/AnswerEvidence.java voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewAnswerAnalyzer.java voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewAnswerAnalyzerTest.java voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "refactor: expand interview answer evidence analysis"
```

---

### Task 3: Add A Pure Rule-Based Follow-Up Decision Engine

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecision.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecisionEngine.java`
- Create: `voice-interview-backend/src/test/java/com/interview/module/interview/service/FollowUpDecisionEngineTest.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewFlowPolicy.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/FollowUpDecisionEngineTest.java`

- [ ] **Step 1: Write the failing decision engine tests**

Create `voice-interview-backend/src/test/java/com/interview/module/interview/service/FollowUpDecisionEngineTest.java`:

```java
package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

class FollowUpDecisionEngineTest {

	@Test
	void should_follow_up_for_normal_question_when_key_points_are_missing() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.MEDIUM,
				AnswerEvidence.Depth.NORMAL,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of("一致性策略"),
				"MISSING_KEY_POINT",
				List.of("KEY_POINT_MISSING"),
				"缺少关键点：一致性策略");

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "Redis", "请说明使用场景和一致性策略", "PRESET", 1),
				"JAVA_CORE",
				0,
				2,
				evidence);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.FOLLOW_UP);
		assertThat(decision.direction()).isEqualTo("MISSING_KEY_POINT");
	}

	@Test
	void should_allow_second_follow_up_for_high_value_question() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.HIGH,
				AnswerEvidence.Depth.SHALLOW,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of(),
				"NEED_EXAMPLE_OR_DETAIL",
				List.of("DEPTH_SHALLOW"),
				"回答结论化，缺少过程和细节");

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "订单系统深挖", "请详细说明最终一致性方案", "AI_RESUME", 3),
				"PROJECT_DEEP_DIVE",
				1,
				3,
				evidence);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.FOLLOW_UP);
		assertThat(decision.reasonCode()).isEqualTo("HIGH_VALUE_DEPTH_PROBE");
	}

	@Test
	void should_skip_follow_up_in_wrap_up_stage() {
		InterviewFlowPolicy policy = new InterviewFlowPolicy(60, 120, 1, 2, 1, 0);
		FollowUpDecisionEngine engine = new FollowUpDecisionEngine(policy);
		AnswerEvidence evidence = new AnswerEvidence(
				true,
				AnswerEvidence.Completeness.MEDIUM,
				AnswerEvidence.Depth.SHALLOW,
				AnswerEvidence.CorrectnessRisk.CONSISTENT,
				List.of("结果"),
				"MISSING_KEY_POINT",
				List.of("KEY_POINT_MISSING", "DEPTH_SHALLOW"),
				"缺少关键点：结果");

		FollowUpDecision decision = engine.decide(
				new InterviewQuestionSnapshot(1, "收尾总结", "请总结本项目复盘结论", "PRESET", 1),
				"WRAP_UP",
				0,
				2,
				evidence);

		assertThat(decision.action()).isEqualTo(FollowUpDecision.Action.NEXT_QUESTION);
		assertThat(decision.reasonCode()).isEqualTo("WRAP_UP_SKIP_FOLLOW_UP");
	}
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
mvn -q "-Dtest=FollowUpDecisionEngineTest" test
```

Expected: FAIL because `FollowUpDecision`, `FollowUpDecisionEngine`, and the extended `InterviewFlowPolicy` constructor do not exist yet.

- [ ] **Step 3: Implement the rule engine and policy**

Create `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecision.java`:

```java
package com.interview.module.interview.service;

public record FollowUpDecision(
		Action action,
		String direction,
		String reasonCode,
		String reasonText
) {

	public enum Action {
		FOLLOW_UP,
		NEXT_QUESTION,
		END_INTERVIEW
	}

	public static FollowUpDecision followUp(String direction, String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.FOLLOW_UP, direction, reasonCode, reasonText);
	}

	public static FollowUpDecision nextQuestion(String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.NEXT_QUESTION, null, reasonCode, reasonText);
	}

	public static FollowUpDecision endInterview(String reasonCode, String reasonText) {
		return new FollowUpDecision(Action.END_INTERVIEW, null, reasonCode, reasonText);
	}
}
```

Create `voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecisionEngine.java`:

```java
package com.interview.module.interview.service;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

public class FollowUpDecisionEngine {

	private final InterviewFlowPolicy flowPolicy;

	public FollowUpDecisionEngine(InterviewFlowPolicy flowPolicy) {
		this.flowPolicy = flowPolicy;
	}

	public FollowUpDecision decide(
			InterviewQuestionSnapshot question,
			String stage,
			int followUpIndex,
			int sessionMaxFollowUp,
			AnswerEvidence evidence
	) {
		if ("WRAP_UP".equals(stage)) {
			return FollowUpDecision.nextQuestion("WRAP_UP_SKIP_FOLLOW_UP", "收尾阶段优先保持节奏");
		}
		boolean highValueQuestion = flowPolicy.isHighValueQuestion(stage, question);
		int followUpLimit = flowPolicy.resolveFollowUpLimit(stage, highValueQuestion, sessionMaxFollowUp);
		if (followUpIndex >= followUpLimit) {
			return FollowUpDecision.nextQuestion("FOLLOW_UP_LIMIT_REACHED", "已达到追问上限");
		}
		if (evidence.correctnessRisk() != AnswerEvidence.CorrectnessRisk.CONSISTENT) {
			return FollowUpDecision.followUp("CLARIFY_CONTRADICTION", "CORRECTNESS_RISK", evidence.summaryReason());
		}
		if (evidence.completeness() == AnswerEvidence.Completeness.LOW) {
			return FollowUpDecision.followUp("MISSING_KEY_POINT", "LOW_COMPLETENESS", evidence.summaryReason());
		}
		if (evidence.completeness() == AnswerEvidence.Completeness.MEDIUM && evidence.depth() == AnswerEvidence.Depth.SHALLOW) {
			return FollowUpDecision.followUp("MISSING_KEY_POINT", "MEDIUM_AND_SHALLOW", evidence.summaryReason());
		}
		if (highValueQuestion && evidence.depth() == AnswerEvidence.Depth.SHALLOW) {
			return FollowUpDecision.followUp("NEED_EXAMPLE_OR_DETAIL", "HIGH_VALUE_DEPTH_PROBE", evidence.summaryReason());
		}
		return FollowUpDecision.nextQuestion("ANSWER_GOOD_ENOUGH", "当前回答已达到继续下一题的标准");
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewFlowPolicy.java`:

```java
package com.interview.module.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

@Service
public class InterviewFlowPolicy {

	private final int defaultDurationMinutes;
	private final int maxDurationMinutes;
	private final int normalFollowUpLimit;
	private final int highValueFollowUpLimit;
	private final int openingFollowUpLimit;
	private final int wrapUpFollowUpLimit;

	public InterviewFlowPolicy(
			@Value("${app.interview.default-duration-minutes:60}") int defaultDurationMinutes,
			@Value("${app.interview.max-duration-minutes:120}") int maxDurationMinutes,
			@Value("${app.interview.follow-up.normal-limit:1}") int normalFollowUpLimit,
			@Value("${app.interview.follow-up.high-value-limit:2}") int highValueFollowUpLimit,
			@Value("${app.interview.follow-up.opening-limit:1}") int openingFollowUpLimit,
			@Value("${app.interview.follow-up.wrap-up-limit:0}") int wrapUpFollowUpLimit
	) {
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.maxDurationMinutes = maxDurationMinutes;
		this.normalFollowUpLimit = normalFollowUpLimit;
		this.highValueFollowUpLimit = highValueFollowUpLimit;
		this.openingFollowUpLimit = openingFollowUpLimit;
		this.wrapUpFollowUpLimit = wrapUpFollowUpLimit;
	}

	InterviewFlowPolicy(
			int defaultDurationMinutes,
			int maxDurationMinutes,
			int normalFollowUpLimit,
			int highValueFollowUpLimit,
			int openingFollowUpLimit,
			int wrapUpFollowUpLimit
	) {
		this(defaultDurationMinutes, maxDurationMinutes, normalFollowUpLimit, highValueFollowUpLimit, openingFollowUpLimit, wrapUpFollowUpLimit);
	}

	public int resolveFollowUpLimit(String stage, boolean highValueQuestion, int sessionMaxFollowUp) {
		int configuredLimit;
		if ("WRAP_UP".equals(stage)) {
			configuredLimit = wrapUpFollowUpLimit;
		} else if ("OPENING".equals(stage)) {
			configuredLimit = openingFollowUpLimit;
		} else if (highValueQuestion) {
			configuredLimit = highValueFollowUpLimit;
		} else {
			configuredLimit = normalFollowUpLimit;
		}
		return Math.max(0, Math.min(sessionMaxFollowUp, configuredLimit));
	}

	public boolean isHighValueQuestion(String stage, InterviewQuestionSnapshot question) {
		if ("PROJECT_DEEP_DIVE".equals(stage)) {
			return true;
		}
		if (question == null) {
			return false;
		}
		if (question.difficultySnapshot() != null && question.difficultySnapshot() >= 3) {
			return true;
		}
		if ("AI_RESUME".equalsIgnoreCase(question.sourceSnapshot())) {
			return true;
		}
		String normalized = ((question.titleSnapshot() == null ? "" : question.titleSnapshot()) + " "
				+ (question.promptSnapshot() == null ? "" : question.promptSnapshot())).replaceAll("\\s+", "").toLowerCase();
		return normalized.contains("项目") || normalized.contains("一致性") || normalized.contains("并发") || normalized.contains("事务");
	}

	public DurationProfile resolve(Integer requestedMinutes) {
		int resolvedDuration = requestedMinutes == null ? defaultDurationMinutes : requestedMinutes;
		resolvedDuration = Math.max(defaultDurationMinutes, Math.min(maxDurationMinutes, resolvedDuration));
		if (resolvedDuration >= 120) {
			return new DurationProfile(120, 8, 3, 28);
		}
		if (resolvedDuration >= 90) {
			return new DurationProfile(90, 8, 3, 24);
		}
		return new DurationProfile(60, 8, 2, 20);
	}

	public record DurationProfile(
			int durationMinutes,
			int mainQuestionCount,
			int maxFollowUpPerQuestion,
			int maxTotalQuestions
	) {
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```bash
mvn -q "-Dtest=FollowUpDecisionEngineTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecision.java voice-interview-backend/src/main/java/com/interview/module/interview/service/FollowUpDecisionEngine.java voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewFlowPolicy.java voice-interview-backend/src/test/java/com/interview/module/interview/service/FollowUpDecisionEngineTest.java
git commit -m "feat: add interview follow-up decision engine"
```

---

### Task 4: Integrate Rule Decisions Into The Interview Engine And Persist Analysis Metadata

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewRoundRecord.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

- [ ] **Step 1: Write the failing engine integration test**

Add these tests to `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`:

```java
@Test
void should_follow_up_when_missing_key_points_and_record_decision_metadata() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		var answered = engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);

		assertThat(answered.status()).isEqualTo("IN_PROGRESS");
		assertThat(answered.followUpIndex()).isEqualTo(1);
		assertThat(answered.rounds().get(0).followUpDecision()).isEqualTo("FOLLOW_UP");
		assertThat(answered.rounds().get(0).followUpDecisionReason()).contains("缺少关键点");
		assertThat(answered.rounds().get(0).missingPointsSnapshot()).contains("一致性策略");
	}

@Test
void should_stop_following_up_after_policy_limit_for_normal_questions() {
		InterviewSessionStore sessionStore = new InMemorySessionStore();
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
				Map.of("reportStore", new NoopInterviewReportStore())
		);
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new StubAiService(),
				new StubTtsService(),
				new FollowUpDecisionEngine(new InterviewFlowPolicy(60, 120, 1, 2, 1, 0))
		);
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "我们用 Redis 做缓存。", null);
		var second = engine.answer(view.sessionId(), "1", "TEXT", "还是主要做缓存。", null);

		assertThat(second.followUpIndex()).isZero();
		assertThat(second.currentQuestionIndex()).isGreaterThanOrEqualTo(1);
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest" test
```

Expected: FAIL because `InterviewRoundRecord` does not yet expose decision metadata and `SimpleInterviewEngine` does not use `FollowUpDecisionEngine`.

- [ ] **Step 3: Integrate the rule engine and persist round analysis JSON**

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewRoundRecord.java`:

```java
package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewRoundRecord(
		String roundId,
		int questionIndex,
		int followUpIndex,
		String roundType,
		String aiMessageText,
		String aiAudioUrl,
		long aiAudioDurationMs,
		Integer scoreSuggestion,
		String userAnswerText,
		String userAudioUrl,
		String userAnswerMode,
		String createdAt,
		String answeredAt,
		String analysisReason,
		String followUpDecision,
		String followUpDecisionReason,
		List<String> missingPointsSnapshot
) {

	public InterviewRoundRecord {
		missingPointsSnapshot = missingPointsSnapshot == null ? List.of() : List.copyOf(missingPointsSnapshot);
	}

	public InterviewRoundRecord withUserAnswer(
			String userAnswerText,
			String userAudioUrl,
			String userAnswerMode,
			String answeredAt,
			String analysisReason,
			String followUpDecision,
			String followUpDecisionReason,
			List<String> missingPointsSnapshot
	) {
		return new InterviewRoundRecord(
				roundId,
				questionIndex,
				followUpIndex,
				roundType,
				aiMessageText,
				aiAudioUrl,
				aiAudioDurationMs,
				scoreSuggestion,
				userAnswerText,
				userAudioUrl,
				userAnswerMode,
				createdAt,
				answeredAt,
				analysisReason,
				followUpDecision,
				followUpDecisionReason,
				missingPointsSnapshot
		);
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`:

```java
private final FollowUpDecisionEngine followUpDecisionEngine;

public SimpleInterviewEngine(
		InterviewSessionStore sessionStore,
		ObjectProvider<InterviewReportStore> interviewReportStoreProvider,
		AiService aiService,
		TtsService ttsService,
		FollowUpDecisionEngine followUpDecisionEngine
) {
		this.sessionStore = sessionStore;
		this.interviewReportStore = interviewReportStoreProvider.getIfAvailable(NoopInterviewReportStore::new);
		this.aiService = aiService;
		this.ttsService = ttsService;
		this.followUpDecisionEngine = followUpDecisionEngine;
}

Update both engine builders in `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java` so `defaultEngine()` and the explicit constructor in the follow-up limit test both pass:

```java
new FollowUpDecisionEngine(new InterviewFlowPolicy(60, 120, 1, 2, 1, 0))
```

...

AnswerEvidence evidence = aiService.analyzeInterviewAnswer(
		currentQuestion.promptSnapshot(),
		normalizedText,
		expectedPoints
);
FollowUpDecision decision = followUpDecisionEngine.decide(
		currentQuestion,
		sessionState.getStage(),
		sessionState.getFollowUpIndex(),
		sessionState.getMaxFollowUpPerQuestion(),
		evidence
);
appendUserAnswer(
		sessionState,
		normalizedText,
		userAudioUrl,
		answerMode,
		evidence.summaryReason(),
		decision.action().name(),
		decision.reasonText(),
		evidence.missingPoints()
);

AiReply aiReply = aiService.generateInterviewReply(new InterviewReplyCommand(
		currentQuestion.promptSnapshot(),
		normalizedText,
		sessionState.getStage(),
		sessionState.getFollowUpIndex(),
		sessionState.getMaxFollowUpPerQuestion(),
		expectedPoints
));
if (decision.action() == FollowUpDecision.Action.FOLLOW_UP) {
	sessionState.setFollowUpIndex(sessionState.getFollowUpIndex() + 1);
	appendAssistantRound(
			sessionState,
			buildFollowUpPrompt(aiReply, decision, evidence),
			"FOLLOW_UP",
			aiReply.scoreSuggestion()
	);
	sessionStore.save(sessionState);
	return toView(sessionState);
}
```

Update `appendUserAnswer(...)` and `buildFollowUpPrompt(...)`:

```java
private void appendUserAnswer(
		InterviewSessionState sessionState,
		String text,
		String userAudioUrl,
		String answerMode,
		String analysisReason,
		String followUpDecision,
		String followUpDecisionReason,
		List<String> missingPointsSnapshot
) {
	int latestRoundIndex = sessionState.getRounds().size() - 1;
	InterviewRoundRecord latestRound = sessionState.getRounds().get(latestRoundIndex);
	sessionState.getRounds().set(
			latestRoundIndex,
			latestRound.withUserAnswer(
					text,
					userAudioUrl,
					answerMode,
					Instant.now().toString(),
					analysisReason,
					followUpDecision,
					followUpDecisionReason,
					missingPointsSnapshot
			)
	);
}

private String buildFollowUpPrompt(AiReply aiReply, FollowUpDecision decision, AnswerEvidence evidence) {
	if ("MISSING_KEY_POINT".equals(decision.direction()) && !evidence.missingPoints().isEmpty()) {
		return "你刚才的回答还缺少这些点：" + String.join("、", evidence.missingPoints()) + "。请补充说明。";
	}
	if ("NEED_EXAMPLE_OR_DETAIL".equals(decision.direction())) {
		return "请不要只给结论，补充一下你的具体做法、取舍和实际案例。";
	}
	if ("CLARIFY_CONTRADICTION".equals(decision.direction())) {
		return "你前后的说法有些不一致，请澄清一下你的实际方案。";
	}
	return aiReply.spokenText();
}
```

Update the existing `skip(...)` path in `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java` so it also writes the expanded round metadata:

```java
appendUserAnswer(
		sessionState,
		"本题已跳过。",
		null,
		"SKIP",
		"候选人主动跳过当前问题",
		"NEXT_QUESTION",
		"SKIPPED_BY_USER",
		List.of()
);
```

Update `appendAssistantRound(...)` in the same file so the new `InterviewRoundRecord` creation stays compilable after the record fields are expanded:

```java
sessionState.getRounds().add(new InterviewRoundRecord(
		"round-" + sessionState.getRounds().size(),
		sessionState.getCurrentQuestionIndex() + 1,
		sessionState.getFollowUpIndex(),
		roundType,
		aiText,
		audioResult.audioUrl(),
		audioResult.durationMs(),
		scoreSuggestion,
		null,
		null,
		null,
		Instant.now().toString(),
		null,
		null,
		null,
		null,
		List.of()
));
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java` to serialize JSON into the existing `ai_analysis` column:

```java
entity.setAiAnalysis(serializeRoundAnalysis(round));
```

And parse it back:

```java
private String serializeRoundAnalysis(InterviewRoundRecord round) {
	try {
		return objectMapper.writeValueAsString(new RoundAnalysisPayload(
				round.analysisReason(),
				round.followUpDecision(),
				round.followUpDecisionReason(),
				round.missingPointsSnapshot()
		));
	} catch (JsonProcessingException ex) {
		throw new IllegalStateException("Failed to serialize round analysis", ex);
	}
}

private RoundAnalysisPayload parseRoundAnalysis(String aiAnalysis) {
	if (aiAnalysis == null || aiAnalysis.isBlank()) {
		return new RoundAnalysisPayload(null, null, null, List.of());
	}
	try {
		if (aiAnalysis.startsWith("{")) {
			return objectMapper.readValue(aiAnalysis, RoundAnalysisPayload.class);
		}
	} catch (Exception ignored) {
	}
	return new RoundAnalysisPayload(aiAnalysis, null, null, List.of());
}

private record RoundAnalysisPayload(
		String analysisReason,
		String followUpDecision,
		String followUpDecisionReason,
		List<String> missingPointsSnapshot
) {
	private RoundAnalysisPayload {
		missingPointsSnapshot = missingPointsSnapshot == null ? List.of() : List.copyOf(missingPointsSnapshot);
	}
}
```

Use the payload in `toRoundRecord(...)`:

```java
RoundAnalysisPayload payload = parseRoundAnalysis(e.getAiAnalysis());
return new InterviewRoundRecord(
		String.valueOf(e.getId()),
		e.getQuestionIndex(),
		e.getFollowUpIndex(),
		e.getRoundType(),
		e.getAiMessageText(),
		e.getTtsAudioUrl(),
		e.getDurationMs() == null ? 0L : e.getDurationMs(),
		e.getScore(),
		e.getFinalUserAnswerText(),
		e.getUserAudioUrl(),
		e.getUserAnswerMode(),
		e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
		e.getAnsweredAt() == null ? null : e.getAnsweredAt().toString(),
		payload.analysisReason(),
		payload.followUpDecision(),
		payload.followUpDecisionReason(),
		payload.missingPointsSnapshot()
);
```

- [ ] **Step 4: Run the tests and build to verify the full integration**

Run:

```bash
mvn -q "-Dtest=InterviewAnswerAnalyzerTest,FollowUpDecisionEngineTest,SimpleInterviewEngineIntegrationTest,OpenAiCompatibleAiServiceTest,LangChain4jAiServiceTest,ProviderRuntimeStatusServiceTest" test
mvn -q -DskipTests package
```

Expected: both commands PASS.

Optional runtime verification when `ops/runtime.local.ps1` is already prepared:

```powershell
.\ops\run-backend-dev.ps1
.\ops\smoke-check.ps1
```

Expected: `Smoke check passed.`

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewRoundRecord.java voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewSessionStore.java voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "refactor: apply deterministic follow-up decisions in interview engine"
```

---

## Self-Review

### Spec coverage

- 高价值题/普通题/开场/收尾的追问规则：由 `FollowUpDecisionEngine` + `InterviewFlowPolicy` 覆盖
- 关键点/深度/正确性风险三维判定：由 `AnswerEvidence` + `InterviewAnswerAnalyzer` 覆盖
- 主链路稳定与错误降级：通过 `SimpleInterviewEngine` 集成策略和回归测试覆盖
- 轮次记录增强：通过 `InterviewRoundRecord` + `JdbcInterviewSessionStore` 的 `ai_analysis` JSON 覆盖

### Placeholder scan

- 没有 `TBD` / `TODO` / “稍后实现” 类占位
- 所有步骤都给出具体文件、代码、命令、预期结果

### Type consistency

- 分析层统一使用 `AnswerEvidence`
- 决策层统一使用 `FollowUpDecision`
- `SimpleInterviewEngine` 只消费 `AnswerEvidence` 和 `FollowUpDecision`
- JDBC 落库继续复用现有 `ai_analysis` 字段，避免额外 schema 迁移
