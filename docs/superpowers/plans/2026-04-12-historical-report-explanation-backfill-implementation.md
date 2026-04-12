# Historical Report Explanation Backfill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lazy explanation backfill for persisted historical interview reports so `GET /api/interviews/{sessionId}/report` can fill missing `overallExplanation` and `questionReports[].explanation`, persist the upgraded report as `v2`, and return the old report safely on failure.

**Architecture:** First extend the report store contract so engine code can read persisted `reportVersion` metadata and save an explicit target version. Then add a rule-only backfill entry in `InterviewReportExplanationService` that fills only missing explanation fields without calling any AI polish path. Finally, wire `SimpleInterviewEngine#getReport(...)` to detect persisted legacy reports, backfill them on read, write them back as `v2`, and degrade to the original persisted report when anything in the backfill path fails.

**Tech Stack:** Java 21, Spring Boot 3.5.x, MyBatis-Plus, Jackson, JUnit 5, AssertJ, Mockito.

---

## File Structure

**Create**
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/PersistedInterviewReport.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java`

**Modify**
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/InterviewReportStore.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/NoopInterviewReportStore.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewReportStore.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

**Do Not Change In This Plan**
- `voice-interview-mobile/**`
- `voice-interview-admin/**`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`
- `voice-interview-backend/src/main/resources/db/**`

---

### Task 1: Add Version-Aware Persisted Report Store Contract

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/PersistedInterviewReport.java`
- Create: `voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/InterviewReportStore.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/NoopInterviewReportStore.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewReportStore.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java`

- [ ] **Step 1: Write the failing store tests**

Create `voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java`:

```java
package com.interview.module.interview.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.entity.ReportEntity;
import com.interview.module.interview.entity.SessionEntity;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.mapper.ReportMapper;
import com.interview.module.interview.mapper.SessionMapper;

class JdbcInterviewReportStoreTest {

	@Test
	void should_return_persisted_report_with_version_metadata() throws Exception {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(42L);
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);

		ReportEntity reportEntity = new ReportEntity();
		reportEntity.setSessionId(42L);
		reportEntity.setReportVersion("v1");
		reportEntity.setReportJson(objectMapper.writeValueAsString(legacyReport("session-1")));
		when(reportMapper.selectOne(any())).thenReturn(reportEntity);

		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, objectMapper);

		PersistedInterviewReport persisted = store.findPersistedReportBySessionId("session-1").orElseThrow();

		assertThat(persisted.reportVersion()).isEqualTo("v1");
		assertThat(persisted.report().sessionId()).isEqualTo("session-1");
		assertThat(persisted.report().overallExplanation()).isNull();
	}

	@Test
	void should_save_report_with_explicit_version() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(42L);
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(reportMapper.selectOne(any())).thenReturn(null);

		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, objectMapper);

		store.save(legacyReport("session-1"), "v2");

		ArgumentCaptor<ReportEntity> entityCaptor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).insert(entityCaptor.capture());
		assertThat(entityCaptor.getValue().getReportVersion()).isEqualTo("v2");
	}

	private static InterviewReportView legacyReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Redis",
				60,
				"整体基础可用，但细节不足。",
				List.of("有基础概念"),
				List.of("一致性策略没展开"),
				List.of("补齐关键点"),
				List.of(new InterviewQuestionReportView(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						60,
						"核心点回答到了，但细节与例子还可以更深入。",
						null
				)),
				null
		);
	}
}
```

- [ ] **Step 2: Run the store tests to verify they fail**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=JdbcInterviewReportStoreTest" test
```

Expected: FAIL because `PersistedInterviewReport` and `findPersistedReportBySessionId(...)` do not exist, and `InterviewReportStore` does not support explicit `reportVersion` writes.

- [ ] **Step 3: Add the persisted report wrapper and extend the store interface**

Create `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/PersistedInterviewReport.java`:

```java
package com.interview.module.interview.engine.store;

import com.interview.module.interview.engine.model.InterviewReportView;

public record PersistedInterviewReport(
		InterviewReportView report,
		String reportVersion
) {

	public PersistedInterviewReport {
		reportVersion = reportVersion == null || reportVersion.isBlank() ? null : reportVersion.trim();
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/InterviewReportStore.java`:

```java
package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public interface InterviewReportStore {

	Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId);

	default Optional<InterviewReportView> findBySessionId(String sessionId) {
		return findPersistedReportBySessionId(sessionId).map(PersistedInterviewReport::report);
	}

	default void save(InterviewReportView report) {
		save(report, "v2");
	}

	void save(InterviewReportView report, String reportVersion);
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/NoopInterviewReportStore.java`:

```java
package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public class NoopInterviewReportStore implements InterviewReportStore {

	@Override
	public Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId) {
		return Optional.empty();
	}

	@Override
	public void save(InterviewReportView report, String reportVersion) {
		// No-op for non-persistent interview session modes.
	}
}
```

- [ ] **Step 4: Implement version-aware JDBC read and save**

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewReportStore.java`:

```java
private static final String LATEST_REPORT_VERSION = "v2";

@Override
public Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId) {
	Optional<Long> internalId = findInternalSessionId(sessionId);
	if (internalId.isEmpty()) {
		return Optional.empty();
	}

	ReportEntity entity = reportMapper.selectOne(
			new LambdaQueryWrapper<ReportEntity>().eq(ReportEntity::getSessionId, internalId.get()));
	if (entity == null) {
		return Optional.empty();
	}

	return Optional.of(new PersistedInterviewReport(
			deserializeReport(entity.getReportJson()),
			entity.getReportVersion()
	));
}

@Override
public void save(InterviewReportView report, String reportVersion) {
	long internalSessionId = findInternalSessionId(report.sessionId())
			.orElseThrow(() -> new IllegalArgumentException("Interview session not found: " + report.sessionId()));
	String reportJson = serializeReport(report);
	String normalizedVersion = normalizeReportVersion(reportVersion);

	ReportEntity existing = reportMapper.selectOne(
			new LambdaQueryWrapper<ReportEntity>().eq(ReportEntity::getSessionId, internalSessionId));

	if (existing == null) {
		ReportEntity entity = new ReportEntity();
		entity.setSessionId(internalSessionId);
		entity.setOverallScore(report.overallScore());
		entity.setOverallComment(report.overallComment());
		entity.setReportJson(reportJson);
		entity.setReportVersion(normalizedVersion);
		reportMapper.insert(entity);
		return;
	}

	existing.setOverallScore(report.overallScore());
	existing.setOverallComment(report.overallComment());
	existing.setReportJson(reportJson);
	existing.setReportVersion(normalizedVersion);
	reportMapper.updateById(existing);
}

private String normalizeReportVersion(String reportVersion) {
	if (reportVersion == null || reportVersion.isBlank()) {
		return LATEST_REPORT_VERSION;
	}
	return reportVersion.trim();
}
```

- [ ] **Step 5: Run the store tests to verify they pass**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=JdbcInterviewReportStoreTest" test
```

Expected: PASS. The JDBC report store should now expose `reportVersion` metadata on read and persist explicit versions on save.

- [ ] **Step 6: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/PersistedInterviewReport.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/InterviewReportStore.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/NoopInterviewReportStore.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewReportStore.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java
git commit -m "feat(report): add persisted report version metadata"
```

---

### Task 2: Add Rule-Only Explanation Backfill Entry In The Report Explanation Service

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`

- [ ] **Step 1: Write the failing rule-only backfill tests**

Append these tests to `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`:

```java
	@Test
	void should_backfill_missing_explanations_without_calling_llm() {
		AiService aiService = mock(AiService.class);
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);

		InterviewReportView backfilled = service.backfillMissingExplanations(
				new InterviewReportView(
						"session-1",
						"COMPLETED",
						"Redis",
						60,
						"整体基础可用，但细节不足。",
						List.of("知道 Redis 是缓存"),
						List.of("一致性策略未展开"),
						List.of("补齐关键点"),
						List.of(new InterviewQuestionReportView(
								1,
								"Redis",
								"请说明 Redis 的使用场景和一致性策略。",
								60,
								"核心点回答到了，但细节与例子还可以更深入。",
								null
						)),
						null
				),
				List.of(new InterviewQuestionSnapshot(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						"PRESET",
						1
				)),
				List.of(new InterviewRoundRecord(
						"r1",
						1,
						0,
						"QUESTION",
						"请说明 Redis 的使用场景和一致性策略。",
						null,
						0L,
						60,
						"我们主要用 Redis 做缓存。",
						null,
						"TEXT",
						"2026-04-12T10:00:00Z",
						"2026-04-12T10:00:10Z",
						"缺少关键点：一致性策略",
						"FOLLOW_UP",
						"缺少关键点：一致性策略",
						List.of("一致性策略")
				))
		);

		assertThat(backfilled.overallExplanation()).isNotNull();
		assertThat(backfilled.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(backfilled.questionReports().get(0).explanation()).isNotNull();
		assertThat(backfilled.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
		verify(aiService, times(0)).polishInterviewReportExplanation(any());
	}

	@Test
	void should_preserve_existing_explanations_when_backfilling_partial_report() {
		AiService aiService = mock(AiService.class);
		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);
		InterviewOverallExplanationView overallExplanation = new InterviewOverallExplanationView(
				"MEDIUM",
				"已有总评解释",
				List.of("已有总评证据"),
				List.of("已有总评建议"),
				"RULE_PLUS_LLM"
		);
		InterviewQuestionExplanationView existingQuestionExplanation = new InterviewQuestionExplanationView(
				"STRONG",
				"已有分题解释",
				List.of("已有分题证据"),
				"已有分题建议",
				"RULE_PLUS_LLM"
		);

		InterviewReportView backfilled = service.backfillMissingExplanations(
				new InterviewReportView(
						"session-2",
						"COMPLETED",
						"Redis",
						78,
						"整体不错。",
						List.of("表达有结构"),
						List.of("细节仍可补强"),
						List.of("继续复盘"),
						List.of(
								new InterviewQuestionReportView(
										1,
										"Redis 使用场景",
										"请说明 Redis 的使用场景。",
										82,
										"回答较完整。",
										existingQuestionExplanation
								),
								new InterviewQuestionReportView(
										2,
										"Redis 一致性",
										"请说明 Redis 的一致性策略。",
										70,
										"核心点回答到了，但细节不够。",
										null
								)
						),
						overallExplanation
				),
				List.of(
						new InterviewQuestionSnapshot(1, "Redis 使用场景", "请说明 Redis 的使用场景。", "PRESET", 1),
						new InterviewQuestionSnapshot(2, "Redis 一致性", "请说明 Redis 的一致性策略。", "PRESET", 1)
				),
				List.of(new InterviewRoundRecord(
						"r2",
						2,
						0,
						"QUESTION",
						"请说明 Redis 的一致性策略。",
						null,
						0L,
						70,
						"我会先更新数据库，再删除缓存。",
						null,
						"TEXT",
						"2026-04-12T10:01:00Z",
						"2026-04-12T10:01:10Z",
						"核心点回答到了，但缺少补偿策略说明",
						"FOLLOW_UP",
						"缺少关键点：补偿策略",
						List.of("补偿策略")
				))
		);

		assertThat(backfilled.overallExplanation()).isEqualTo(overallExplanation);
		assertThat(backfilled.questionReports().get(0).explanation()).isEqualTo(existingQuestionExplanation);
		assertThat(backfilled.questionReports().get(1).explanation()).isNotNull();
		verify(aiService, times(0)).polishInterviewReportExplanation(any());
	}
```

- [ ] **Step 2: Run the service tests to verify they fail**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest" test
```

Expected: FAIL because `backfillMissingExplanations(...)` does not exist and `enrichReport(...)` still owns all explanation assembly logic.

- [ ] **Step 3: Implement the rule-only backfill method and reuse it inside enrich**

Update `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`:

```java
public InterviewReportView backfillMissingExplanations(
		InterviewReportView report,
		List<InterviewQuestionSnapshot> questions,
		List<InterviewRoundRecord> rounds
) {
	if (report == null) {
		return null;
	}

	List<InterviewQuestionReportView> backfilledQuestionReports = new ArrayList<>();
	for (InterviewQuestionReportView questionReport : safeQuestionReports(report.questionReports())) {
		InterviewQuestionExplanationView explanation = questionReport.explanation();
		if (explanation == null) {
			InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
			explanation = buildQuestionExplanation(
					question,
					questionReport,
					roundsForQuestion(rounds, questionReport.questionIndex())
			);
		}
		backfilledQuestionReports.add(new InterviewQuestionReportView(
				questionReport.questionIndex(),
				questionReport.title(),
				questionReport.prompt(),
				questionReport.score(),
				questionReport.summary(),
				explanation
		));
	}

	InterviewOverallExplanationView overallExplanation = report.overallExplanation();
	if (overallExplanation == null) {
		overallExplanation = buildOverallExplanation(
				report.overallScore(),
				backfilledQuestionReports,
				rounds
		);
	}

	return new InterviewReportView(
			report.sessionId(),
			report.status(),
			report.title(),
			report.overallScore(),
			report.overallComment(),
			report.strengths(),
			report.weaknesses(),
			report.suggestions(),
			List.copyOf(backfilledQuestionReports),
			overallExplanation
	);
}

public InterviewReportView enrichReport(
		InterviewReportView report,
		List<InterviewQuestionSnapshot> questions,
		List<InterviewRoundRecord> rounds
) {
	InterviewReportView ruleBackfilledReport = backfillMissingExplanations(report, questions, rounds);
	if (ruleBackfilledReport == null) {
		return null;
	}

	List<InterviewQuestionReportView> explainedQuestionReports = new ArrayList<>();
	for (InterviewQuestionReportView questionReport : safeQuestionReports(ruleBackfilledReport.questionReports())) {
		InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
		InterviewQuestionExplanationView explanation = polishQuestionExplanation(
				question,
				questionReport,
				questionReport.explanation()
		);
		explainedQuestionReports.add(new InterviewQuestionReportView(
				questionReport.questionIndex(),
				questionReport.title(),
				questionReport.prompt(),
				questionReport.score(),
				questionReport.summary(),
				explanation
		));
	}

	return new InterviewReportView(
			ruleBackfilledReport.sessionId(),
			ruleBackfilledReport.status(),
			ruleBackfilledReport.title(),
			ruleBackfilledReport.overallScore(),
			ruleBackfilledReport.overallComment(),
			ruleBackfilledReport.strengths(),
			ruleBackfilledReport.weaknesses(),
			ruleBackfilledReport.suggestions(),
			List.copyOf(explainedQuestionReports),
			polishOverallExplanation(ruleBackfilledReport, ruleBackfilledReport.overallExplanation())
	);
}
```

- [ ] **Step 4: Run the service tests to verify they pass**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest" test
```

Expected: PASS. The service should now expose a rule-only backfill path that fills only missing explanations and never calls the LLM polish contract.

- [ ] **Step 5: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java
git commit -m "feat(report): add rule-only explanation backfill"
```

---

### Task 3: Wire Lazy Backfill Into SimpleInterviewEngine Read Path

**Files:**
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

- [ ] **Step 1: Write the failing engine-level lazy-backfill tests**

Append these tests and helpers to `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`:

```java
	// Add imports:
	// import com.interview.module.ai.service.InterviewReportExplanationCommand;
	// import com.interview.module.ai.service.InterviewReportExplanationResult;
	// import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
	// import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
	// import com.interview.module.interview.engine.store.PersistedInterviewReport;

	@Test
	void should_backfill_legacy_persisted_report_and_upgrade_to_v2() {
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession("session-legacy"));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore(
				new PersistedInterviewReport(legacyPersistedReport("session-legacy"), "v1")
		);
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new ThrowingPolishAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewReportView report = engine.getReport("session-legacy", "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.savedVersion).isEqualTo("v2");
	}

	@Test
	void should_backfill_missing_explanations_even_when_persisted_report_is_v2() {
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession("session-v2"));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore(
				new PersistedInterviewReport(legacyPersistedReport("session-v2"), "v2")
		);
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new ThrowingPolishAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewReportView report = engine.getReport("session-v2", "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveCalls).isEqualTo(1);
		assertThat(reportStore.savedVersion).isEqualTo("v2");
	}

	@Test
	void should_skip_backfill_when_persisted_report_already_has_explanations() {
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession("session-ready"));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore(
				new PersistedInterviewReport(backfilledPersistedReport("session-ready"), "v2")
		);
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new ThrowingPolishAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewReportView report = engine.getReport("session-ready", "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(reportStore.saveCalls).isZero();
	}

	@Test
	void should_return_old_persisted_report_when_backfill_save_fails() {
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(completedRedisSession("session-fail"));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore(
				new PersistedInterviewReport(legacyPersistedReport("session-fail"), "v1")
		);
		reportStore.failOnSave = true;
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new ThrowingPolishAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewReportView report = engine.getReport("session-fail", "1");

		assertThat(report.overallExplanation()).isNull();
		assertThat(report.questionReports().get(0).explanation()).isNull();
		assertThat(reportStore.saveCalls).isEqualTo(1);
	}

	@Test
	void should_return_old_persisted_report_when_session_context_is_incomplete() {
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		sessionStore.save(incompleteCompletedSession("session-incomplete"));
		RecordingPersistedReportStore reportStore = new RecordingPersistedReportStore(
				new PersistedInterviewReport(legacyPersistedReport("session-incomplete"), "v1")
		);
		StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("reportStore", reportStore));
		SimpleInterviewEngine engine = new SimpleInterviewEngine(
				sessionStore,
				beanFactory.getBeanProvider(InterviewReportStore.class),
				new ThrowingPolishAiService(),
				new StubTtsService(),
				defaultDecisionEngine()
		);

		InterviewReportView report = engine.getReport("session-incomplete", "1");

		assertThat(report.overallExplanation()).isNull();
		assertThat(report.questionReports().get(0).explanation()).isNull();
		assertThat(reportStore.saveCalls).isZero();
	}

	private static InterviewSessionState completedRedisSession(String sessionId) {
		InterviewSessionState state = new InterviewSessionState(
				sessionId,
				"1",
				"tester",
				List.of(new InterviewQuestionSnapshot(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						"PRESET",
						1
				)),
				"WRAP_UP",
				60,
				2,
				null,
				1.0
		);
		state.setStatus("COMPLETED");
		state.getRounds().add(new InterviewRoundRecord(
				"r1",
				1,
				0,
				"QUESTION",
				"请说明 Redis 的使用场景和一致性策略。",
				null,
				0L,
				60,
				"我们主要用 Redis 做缓存。",
				null,
				"TEXT",
				"2026-04-12T10:00:00Z",
				"2026-04-12T10:00:10Z",
				"缺少关键点：一致性策略",
				"FOLLOW_UP",
				"缺少关键点：一致性策略",
				List.of("一致性策略")
		));
		return state;
	}

	private static InterviewSessionState incompleteCompletedSession(String sessionId) {
		InterviewSessionState state = new InterviewSessionState(
				sessionId,
				"1",
				"tester",
				List.of(),
				"WRAP_UP",
				60,
				2,
				null,
				1.0
		);
		state.setStatus("COMPLETED");
		return state;
	}

	private static InterviewReportView legacyPersistedReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Redis",
				60,
				"整体基础可用，但细节不足。",
				List.of("知道 Redis 是缓存"),
				List.of("一致性策略未展开"),
				List.of("补齐关键点"),
				List.of(new InterviewQuestionReportView(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						60,
						"核心点回答到了，但细节与例子还可以更深入。",
						null
				)),
				null
		);
	}

	private static InterviewReportView backfilledPersistedReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Redis",
				60,
				"整体基础可用，但细节不足。",
				List.of("知道 Redis 是缓存"),
				List.of("一致性策略未展开"),
				List.of("补齐关键点"),
				List.of(new InterviewQuestionReportView(
						1,
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						60,
						"核心点回答到了，但细节与例子还可以更深入。",
						new InterviewQuestionExplanationView(
								"MEDIUM",
								"已有分题解释",
								List.of("已有分题证据"),
								"已有分题建议",
								"RULE"
						)
				)),
				new InterviewOverallExplanationView(
						"MEDIUM",
						"已有总评解释",
						List.of("已有总评证据"),
						List.of("已有总评建议"),
						"RULE"
				)
		);
	}

	private static final class RecordingPersistedReportStore implements InterviewReportStore {
		private final PersistedInterviewReport persistedReport;
		private int saveCalls = 0;
		private String savedVersion;
		private boolean failOnSave = false;

		private RecordingPersistedReportStore(PersistedInterviewReport persistedReport) {
			this.persistedReport = persistedReport;
		}

		@Override
		public Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId) {
			return persistedReport.report().sessionId().equals(sessionId)
					? Optional.of(persistedReport)
					: Optional.empty();
		}

		@Override
		public void save(InterviewReportView report, String reportVersion) {
			saveCalls++;
			savedVersion = reportVersion;
			if (failOnSave) {
				throw new IllegalStateException("save failed");
			}
		}
	}

	private static final class ThrowingPolishAiService extends StubAiService {
		@Override
		public InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
			throw new AssertionError("historical backfill must not call polishInterviewReportExplanation");
		}
	}
```

- [ ] **Step 2: Run the engine tests to verify they fail**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_backfill_legacy_persisted_report_and_upgrade_to_v2+should_backfill_missing_explanations_even_when_persisted_report_is_v2+should_skip_backfill_when_persisted_report_already_has_explanations+should_return_old_persisted_report_when_backfill_save_fails+should_return_old_persisted_report_when_session_context_is_incomplete" test
```

Expected: FAIL because `SimpleInterviewEngine#getReport(...)` still reads persisted reports directly and never attempts lazy explanation backfill or explicit `v2` saves.

- [ ] **Step 3: Implement lazy backfill orchestration in the engine**

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`:

```java
private static final Logger log = LoggerFactory.getLogger(SimpleInterviewEngine.class);
private static final String LATEST_REPORT_VERSION = "v2";

@Override
public InterviewReportView getReport(String sessionId, String requesterUserId) {
	InterviewSessionState sessionState = requireSession(sessionId, requesterUserId);
	if ("IN_PROGRESS".equals(sessionState.getStatus())) {
		return toReportView(sessionState);
	}

	Optional<PersistedInterviewReport> persisted = interviewReportStore.findPersistedReportBySessionId(sessionId);
	if (persisted.isEmpty()) {
		return persistReport(sessionState);
	}
	return maybeBackfillPersistedReport(sessionState, persisted.get());
}

private InterviewReportView maybeBackfillPersistedReport(
		InterviewSessionState sessionState,
		PersistedInterviewReport persistedReport
) {
	InterviewReportView report = persistedReport.report();
	if (!hasMissingExplanation(report)) {
		log.debug(
				"report explanation backfill skipped: sessionId={}, reportVersion={}",
				sessionState.getSessionId(),
				persistedReport.reportVersion()
		);
		return report;
	}
	if (!hasBackfillContext(sessionState)) {
		log.warn(
				"report explanation backfill failed: sessionId={}, reportVersion={}, reason=incomplete_context",
				sessionState.getSessionId(),
				persistedReport.reportVersion()
		);
		return report;
	}

	try {
		InterviewReportView backfilled = interviewReportExplanationService.backfillMissingExplanations(
				report,
				sessionState.getQuestions(),
				sessionState.getRounds()
		);
		interviewReportStore.save(backfilled, LATEST_REPORT_VERSION);
		log.info(
				"report explanation backfill succeeded: sessionId={}, reportVersion={}",
				sessionState.getSessionId(),
				persistedReport.reportVersion()
		);
		return backfilled;
	} catch (RuntimeException ex) {
		log.warn(
				"report explanation backfill failed: sessionId={}, reportVersion={}",
				sessionState.getSessionId(),
				persistedReport.reportVersion(),
				ex
		);
		return report;
	}
}

private boolean hasBackfillContext(InterviewSessionState sessionState) {
	return sessionState != null
			&& sessionState.getQuestions() != null
			&& !sessionState.getQuestions().isEmpty()
			&& sessionState.getRounds() != null
			&& !sessionState.getRounds().isEmpty();
}

private boolean hasMissingExplanation(InterviewReportView report) {
	if (report == null || report.overallExplanation() == null) {
		return true;
	}
	for (InterviewQuestionReportView questionReport : report.questionReports() == null
			? List.<InterviewQuestionReportView>of()
			: report.questionReports()) {
		if (questionReport.explanation() == null) {
			return true;
		}
	}
	return false;
}

private InterviewReportView persistReport(InterviewSessionState sessionState) {
	InterviewReportView report = toReportView(sessionState);
	interviewReportStore.save(report, LATEST_REPORT_VERSION);
	return report;
}
```

- [ ] **Step 4: Run the focused engine tests to verify they pass**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=SimpleInterviewEngineIntegrationTest#should_backfill_legacy_persisted_report_and_upgrade_to_v2+should_backfill_missing_explanations_even_when_persisted_report_is_v2+should_skip_backfill_when_persisted_report_already_has_explanations+should_return_old_persisted_report_when_backfill_save_fails+should_return_old_persisted_report_when_session_context_is_incomplete" test
```

Expected: PASS. Historical persisted reports with missing explanations should be backfilled on read and saved as `v2`, already-complete reports should skip backfill, and save failures or incomplete context should return the original persisted report instead of throwing.

- [ ] **Step 5: Run the regression suite for the whole feature slice**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=JdbcInterviewReportStoreTest,InterviewReportExplanationServiceTest,SimpleInterviewEngineIntegrationTest" test
```

Expected: PASS. The report store contract, rule-only backfill path, and engine lazy backfill orchestration should all work together without regressing the current report explanation behavior.

- [ ] **Step 6: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "feat(report): backfill historical explanations on read"
```

---

### Self-Review

**Spec coverage**
- [ ] Requirement `只补 overallExplanation 和 questionReports[].explanation` is covered by Task 2 `backfillMissingExplanations(...)`, which copies all existing report fields and fills only null explanation fields.
- [ ] Requirement `Engine 读路径懒回填` is covered by Task 3 `SimpleInterviewEngine#getReport(...)` orchestration.
- [ ] Requirement `reportVersion 升级到 v2` is covered by Task 1 store write support and Task 3 explicit `save(backfilled, "v2")`.
- [ ] Requirement `失败降级返回旧 report，不报 500` is covered by Task 3 `maybeBackfillPersistedReport(...)` plus the save-failure integration test.
- [ ] Requirement `questions / rounds 不完整时安全降级` is covered by Task 3 `hasBackfillContext(...)` and the incomplete-context integration test.
- [ ] Requirement `回填不走 LLM` is covered by Task 2 no-LLM service tests and Task 3 `ThrowingPolishAiService` integration tests.

**Placeholder scan**
- [ ] Run:

```bash
rg -n "TODO|TBD|implement later|fill in details|Similar to Task|appropriate error handling" docs/superpowers/plans/2026-04-12-historical-report-explanation-backfill-implementation.md | rg -v "^[0-9]+:rg -n"
```

Expected: no output

**Type consistency**
- [ ] Run:

```bash
rg -n "PersistedInterviewReport|findPersistedReportBySessionId|backfillMissingExplanations|LATEST_REPORT_VERSION|hasMissingExplanation|hasBackfillContext" docs/superpowers/plans/2026-04-12-historical-report-explanation-backfill-implementation.md | rg -v "^[0-9]+:rg -n"
```

Expected: each identifier appears with a single spelling across all tasks.
