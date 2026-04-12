# Report Explanation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build diagnostic report explanations for `voice-interview-mobile` by adding backend structured explanation models, rule-based explanation generation, optional LLM polishing with fallback, and mobile report rendering.

**Architecture:** First extend backend report DTOs with structured explanation objects and generate stable rule-based explanations from existing report and round data. Then add an AI polishing contract that rewrites only the expression of those explanations while preserving rule conclusions and falling back cleanly to rule text. Finally, wire the new fields into `voice-interview-mobile` so the report page renders overall and per-question diagnostic cards without breaking existing report content.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Jackson, JUnit 5, AssertJ, Mockito, Vue 3, uni-app, TypeScript, vue-tsc.

---

## File Structure

**Create**
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewOverallExplanationView.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionExplanationView.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationCommand.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationResult.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReportExplanationAssistant.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`

**Modify**
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewReportView.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionReportView.java`
- `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java`
- `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`
- `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`
- `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- `voice-interview-mobile/src/types/interview.ts`
- `voice-interview-mobile/src/pages/interview/report.vue`

**Do Not Change In This Plan**
- `voice-interview-admin/**`
- `voice-interview-mobile/src/pages/interview/session.vue`
- `voice-interview-backend/src/main/resources/db/schema.sql`
- `voice-interview-backend/src/main/java/com/interview/module/asr/**`
- `voice-interview-backend/src/main/java/com/interview/module/tts/**`

---

### Task 1: Add Structured Explanation DTOs And Rule-Based Report Generation

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewOverallExplanationView.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionExplanationView.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`
- Create: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewReportView.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionReportView.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

Create `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`:

```java
package com.interview.module.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

class InterviewReportExplanationServiceTest {

	@Test
	void should_build_rule_based_overall_explanation() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewOverallExplanationView explanation = service.buildOverallExplanation(
				68,
				List.of(
						new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 60, "核心点回答到了，但细节与例子还可以更深入。", null),
						new InterviewQuestionReportView(2, "消息队列", "请说明消息可靠性方案。", 72, "回答较完整。", null)
				),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 60, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略")),
						new InterviewRoundRecord("r2", 2, 0, "QUESTION", "题目", null, 0L, 72, "我们通过重试和死信队列保证消息可靠性。", null, "TEXT", "2026-04-11T00:01:00Z", "2026-04-11T00:01:10Z", "回答较完整", "NEXT_QUESTION", "当前回答已达到继续下一题的标准", List.of())
				)
		);

		assertThat(explanation.level()).isEqualTo("MEDIUM");
		assertThat(explanation.summaryText()).contains("整体");
		assertThat(explanation.evidencePoints()).isNotEmpty();
		assertThat(explanation.improvementSuggestions()).isNotEmpty();
		assertThat(explanation.generatedBy()).isEqualTo("RULE");
	}

	@Test
	void should_build_question_explanation_from_missing_points() {
		InterviewReportExplanationService service = new InterviewReportExplanationService();

		InterviewQuestionExplanationView explanation = service.buildQuestionExplanation(
				new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1),
				new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 60, "核心点回答到了，但细节与例子还可以更深入。", null),
				List.of(
						new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 60, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略"))
				)
		);

		assertThat(explanation.performanceLevel()).isEqualTo("MEDIUM");
		assertThat(explanation.summaryText()).contains("一致性策略");
		assertThat(explanation.evidencePoints()).contains("缺少关键点：一致性策略");
		assertThat(explanation.improvementSuggestion()).contains("一致性策略");
		assertThat(explanation.generatedBy()).isEqualTo("RULE");
	}
}
```

Add this test to `voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java`:

```java
	@Test
	void should_include_rule_explanations_in_report_view() {
		var engine = defaultEngine();
		var view = engine.startSession(
				List.of(new InterviewQuestionCard("Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", null, null, 1)),
				60,
				2,
				new InterviewSessionOwner("1", "tester"),
				null,
				null
		);

		engine.answer(view.sessionId(), "1", "TEXT", "我们主要用 Redis 做缓存。", null);
		InterviewReportView report = engine.getReport(view.sessionId(), "1");

		assertThat(report.overallExplanation()).isNotNull();
		assertThat(report.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(report.questionReports()).hasSize(1);
		assertThat(report.questionReports().get(0).explanation()).isNotNull();
		assertThat(report.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest,SimpleInterviewEngineIntegrationTest#should_include_rule_explanations_in_report_view" test
```

Expected: FAIL because explanation DTOs and `InterviewReportExplanationService` do not exist, and `InterviewReportView` / `InterviewQuestionReportView` do not expose explanation fields.

- [ ] **Step 3: Add explanation DTOs and extend report view records**

Create `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewOverallExplanationView.java`:

```java
package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewOverallExplanationView(
		String level,
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions,
		String generatedBy
) {
	public InterviewOverallExplanationView {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
		generatedBy = generatedBy == null ? "RULE" : generatedBy;
	}
}
```

Create `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionExplanationView.java`:

```java
package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewQuestionExplanationView(
		String performanceLevel,
		String summaryText,
		List<String> evidencePoints,
		String improvementSuggestion,
		String generatedBy
) {
	public InterviewQuestionExplanationView {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		generatedBy = generatedBy == null ? "RULE" : generatedBy;
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionReportView.java`:

```java
package com.interview.module.interview.engine.model;

public record InterviewQuestionReportView(
		int questionIndex,
		String title,
		String prompt,
		Integer score,
		String summary,
		InterviewQuestionExplanationView explanation
) {
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewReportView.java`:

```java
package com.interview.module.interview.engine.model;

import java.util.List;

public record InterviewReportView(
		String sessionId,
		String status,
		String title,
		Integer overallScore,
		String overallComment,
		List<String> strengths,
		List<String> weaknesses,
		List<String> suggestions,
		List<InterviewQuestionReportView> questionReports,
		InterviewOverallExplanationView overallExplanation
) {
}
```

- [ ] **Step 4: Implement the minimal rule service and wire report generation**

Create `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`:

```java
package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

public class InterviewReportExplanationService {

	public InterviewReportView enrichReport(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		List<InterviewQuestionReportView> explainedQuestions = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(report)) {
			InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
			InterviewQuestionExplanationView explanation = buildQuestionExplanation(
					question,
					questionReport,
					roundsForQuestion(rounds, questionReport.questionIndex())
			);
			explainedQuestions.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					explanation
			));
		}

		InterviewOverallExplanationView overallExplanation = buildOverallExplanation(
				report.overallScore(),
				explainedQuestions,
				rounds
		);

		return new InterviewReportView(
				report.sessionId(),
				report.status(),
				report.title(),
				report.overallScore(),
				report.overallComment(),
				report.strengths(),
				report.weaknesses(),
				report.suggestions(),
				List.copyOf(explainedQuestions),
				overallExplanation
		);
	}

	public InterviewOverallExplanationView buildOverallExplanation(
			Integer overallScore,
			List<InterviewQuestionReportView> questionReports,
			List<InterviewRoundRecord> rounds
	) {
		String level = levelFromScore(overallScore);
		int followUpCount = 0;
		int missingPointRounds = 0;
		int strongQuestionCount = 0;
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if ("FOLLOW_UP".equals(round.roundType())) {
				followUpCount++;
			}
			if (!round.missingPointsSnapshot().isEmpty()) {
				missingPointRounds++;
			}
		}
		for (InterviewQuestionReportView questionReport : questionReports == null ? List.<InterviewQuestionReportView>of() : questionReports) {
			if (questionReport.score() != null && questionReport.score() >= 80) {
				strongQuestionCount++;
			}
		}

		String summaryText = switch (level) {
			case "STRONG" -> "整体表现比较稳定，多数题目能站稳结论，追问下也有一定延展能力。";
			case "MEDIUM" -> "整体基础可用，但关键点覆盖和追问深度还不够稳定，结论能立住，细节仍需补强。";
			default -> "整体回答覆盖度和稳定性都偏弱，遇到关键场景和追问时容易暴露缺口。";
		};

		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		if (strongQuestionCount > 0) {
			evidencePoints.add("有 " + strongQuestionCount + " 道题最终得分达到 80 分以上，说明基础表达能力已经建立。");
		}
		if (missingPointRounds > 0) {
			evidencePoints.add("共有 " + missingPointRounds + " 个轮次被识别出关键点缺失，说明回答覆盖度还不够稳定。");
		}
		if (followUpCount > 0) {
			evidencePoints.add("本轮出现 " + followUpCount + " 次追问，说明部分题目仍需要靠补充说明才能站稳。");
		}
		if (evidencePoints.isEmpty()) {
			evidencePoints.add("当前有效答题记录有限，总评主要依据现有得分和题目摘要生成。");
		}

		LinkedHashSet<String> improvementSuggestions = new LinkedHashSet<>();
		if (missingPointRounds > 0) {
			improvementSuggestions.add("先按题型整理每题必须覆盖的关键点，避免回答只停留在结论。");
		}
		if (followUpCount > 1) {
			improvementSuggestions.add("每个高频项目题都准备一版“背景、方案、取舍、结果”的固定表达。");
		}
		if (overallScore != null && overallScore >= 80) {
			improvementSuggestions.add("继续补强追问时的细节深度，把高分题稳定成可复用模板。");
		}
		if (improvementSuggestions.isEmpty()) {
			improvementSuggestions.add("继续保持每周 2 到 3 次复盘，重点看被追问最多的题。");
		}

		return new InterviewOverallExplanationView(
				level,
				summaryText,
				limit(evidencePoints, 3),
				limit(improvementSuggestions, 2),
				"RULE"
		);
	}

	public InterviewQuestionExplanationView buildQuestionExplanation(
			InterviewQuestionSnapshot question,
			InterviewQuestionReportView questionReport,
			List<InterviewRoundRecord> questionRounds
	) {
		String performanceLevel = levelFromScore(questionReport == null ? null : questionReport.score());
		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		LinkedHashSet<String> missingPoints = new LinkedHashSet<>();
		int followUpCount = 0;

		for (InterviewRoundRecord round : safeRounds(questionRounds)) {
			if ("FOLLOW_UP".equals(round.roundType())) {
				followUpCount++;
			}
			if (round.analysisReason() != null && !round.analysisReason().isBlank()) {
				evidencePoints.add(round.analysisReason().trim());
			}
			if (!round.missingPointsSnapshot().isEmpty()) {
				missingPoints.addAll(round.missingPointsSnapshot());
			}
		}

		String summaryText;
		String improvementSuggestion;
		if (!missingPoints.isEmpty()) {
			String joinedMissingPoints = String.join("、", missingPoints);
			evidencePoints.add("缺少关键点：" + joinedMissingPoints);
			summaryText = "这题核心覆盖不完整，尤其是 " + joinedMissingPoints + " 没有展开。";
			improvementSuggestion = "补充 " + joinedMissingPoints + "，再说明你的实际方案和取舍。";
		} else if (followUpCount >= 2 && (questionReport == null || questionReport.score() == null || questionReport.score() < 80)) {
			evidencePoints.add("同一题出现 " + followUpCount + " 次追问，说明细节展开还不够稳定。");
			summaryText = "这题基础回答有了，但连续追问后细节深度仍然不够稳定。";
			improvementSuggestion = "围绕这题补一版更完整的案例表达，重点准备过程、权衡和结果。";
		} else if (containsRiskSignal(evidencePoints)) {
			summaryText = "这题回答与题目核心的对齐度不够，存在答偏或前后不一致的风险。";
			improvementSuggestion = "先回到题目本身，明确题干要点，再按“结论 + 依据 + 方案”顺序回答。";
		} else if (questionReport != null && questionReport.score() != null && questionReport.score() >= 80) {
			evidencePoints.add("最终得分较高且追问较少，说明回答完整度和稳定性都还可以。");
			summaryText = "这题回答比较完整，核心点和表达稳定性都达到了较好水平。";
			improvementSuggestion = "继续保留这题的表达结构，后续只需要再补强细节深度。";
		} else {
			String title = question == null ? "当前题目" : question.titleSnapshot();
			evidencePoints.add("当前摘要显示核心点已覆盖，但高价值细节和例子还能继续补充。");
			summaryText = "这题基础结论能给出来，但还缺少让面试官快速信服的细节支撑。";
			improvementSuggestion = "围绕 “" + title + "” 准备一版更具体的真实场景和设计取舍。";
		}

		return new InterviewQuestionExplanationView(
				performanceLevel,
				summaryText,
				limit(evidencePoints, 2),
				improvementSuggestion,
				"RULE"
		);
	}

	private List<InterviewQuestionReportView> safeQuestionReports(InterviewReportView report) {
		return report == null || report.questionReports() == null ? List.of() : report.questionReports();
	}

	private List<InterviewRoundRecord> safeRounds(List<InterviewRoundRecord> rounds) {
		return rounds == null ? List.of() : rounds;
	}

	private InterviewQuestionSnapshot findQuestion(List<InterviewQuestionSnapshot> questions, int questionIndex) {
		for (InterviewQuestionSnapshot question : questions == null ? List.<InterviewQuestionSnapshot>of() : questions) {
			if (question.questionIndex() == questionIndex) {
				return question;
			}
		}
		return new InterviewQuestionSnapshot(questionIndex, "题目 " + questionIndex, "请补充你的回答。");
	}

	private List<InterviewRoundRecord> roundsForQuestion(List<InterviewRoundRecord> rounds, int questionIndex) {
		List<InterviewRoundRecord> result = new ArrayList<>();
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if (round.questionIndex() == questionIndex) {
				result.add(round);
			}
		}
		return result;
	}

	private boolean containsRiskSignal(LinkedHashSet<String> evidencePoints) {
		for (String evidencePoint : evidencePoints) {
			if (evidencePoint.contains("不一致") || evidencePoint.contains("答偏") || evidencePoint.contains("回到题目")) {
				return true;
			}
		}
		return false;
	}

	private String levelFromScore(Integer score) {
		if (score == null) {
			return "MEDIUM";
		}
		if (score >= 80) {
			return "STRONG";
		}
		if (score >= 60) {
			return "MEDIUM";
		}
		return "WEAK";
	}

	private List<String> limit(LinkedHashSet<String> values, int maxSize) {
		List<String> result = new ArrayList<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				result.add(value.trim());
			}
			if (result.size() == maxSize) {
				break;
			}
		}
		return List.copyOf(result);
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`:

```java
	private final InterviewReportExplanationService interviewReportExplanationService;

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
		this.interviewReportExplanationService = new InterviewReportExplanationService();
	}
```

Replace the report-building tail of `toReportView(...)` with:

```java
		for (InterviewQuestionSnapshot question : sessionState.getQuestions()) {
			InterviewRoundRecord round = latestRoundByQuestion.get(question.questionIndex());
			Integer score = round == null ? null : round.scoreSuggestion();
			String summary = score == null
					? "当前题目还没有形成有效评分。"
					: score >= 80
						? "回答较完整，追问表现也比较稳定。"
						: score >= 60
							? "核心点回答到了，但细节与例子还可以更深入。"
							: "回答覆盖度不足，建议回到基础概念和经典场景重新练。";
			questionReports.add(new InterviewQuestionReportView(
					question.questionIndex(),
					question.titleSnapshot(),
					question.promptSnapshot(),
					score,
					summary,
					null
			));
		}

		String title = sessionState.getQuestions().isEmpty() ? "模拟面试报告" : sessionState.getQuestions().get(0).titleSnapshot();
		InterviewReportView baseReport = new InterviewReportView(
				sessionState.getSessionId(),
				sessionState.getStatus(),
				title,
				overallScore,
				overallComment,
				List.copyOf(strengths),
				List.copyOf(weaknesses),
				List.copyOf(suggestions),
				List.copyOf(questionReports),
				null
		);
		return interviewReportExplanationService.enrichReport(
				baseReport,
				sessionState.getQuestions(),
				sessionState.getRounds()
		);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest,SimpleInterviewEngineIntegrationTest#should_include_rule_explanations_in_report_view" test
```

Expected: PASS. The unit test should verify the rule generator, and the integration test should verify that `/report`-shaped data now includes `overallExplanation` and per-question `explanation`.

- [ ] **Step 6: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewOverallExplanationView.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionExplanationView.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewQuestionReportView.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/model/InterviewReportView.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java
git commit -m "feat(report): 增加规则解释模型"
```

---

### Task 2: Add AI Polishing Contract, Provider Implementations, And Fallback

**Files:**
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationCommand.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationResult.java`
- Create: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReportExplanationAssistant.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java`
- Modify: `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`
- Modify: `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`
- Test: `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`

- [ ] **Step 1: Write the failing polishing and fallback tests**

Append these tests to `voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java`:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.interview.engine.model.InterviewReportView;

	@Test
	void should_use_polished_copy_when_ai_returns_valid_result() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenReturn(new InterviewReportExplanationResult(
						"整体基础可用，但关键点覆盖度仍不稳定。",
						List.of("多题存在关键点缺失", "追问后细节展开不够稳定"),
						List.of("按题型整理固定回答框架")
				));

		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);
		InterviewReportView baseReport = new InterviewReportView(
				"session-1",
				"COMPLETED",
				"模拟面试报告",
				68,
				"整体基础可用，但关键场景的深度和取舍表达还需要继续补强。",
				List.of("基础表达成立"),
				List.of("关键点覆盖不稳定"),
				List.of("准备项目追问模板"),
				List.of(new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 60, "核心点回答到了，但细节与例子还可以更深入。", null)),
				null
		);

		InterviewReportView enriched = service.enrichReport(
				baseReport,
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 60, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略")))
		);

		assertThat(enriched.overallExplanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
		assertThat(enriched.overallExplanation().summaryText()).contains("关键点覆盖度");
		assertThat(enriched.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE_PLUS_LLM");
	}

	@Test
	void should_fall_back_to_rule_copy_when_ai_polish_throws() {
		AiService aiService = mock(AiService.class);
		when(aiService.polishInterviewReportExplanation(any()))
				.thenThrow(new IllegalStateException("provider unavailable"));

		InterviewReportExplanationService service = new InterviewReportExplanationService(aiService);
		InterviewReportView baseReport = new InterviewReportView(
				"session-2",
				"COMPLETED",
				"模拟面试报告",
				55,
				"整体表现偏弱，建议先回到核心概念、场景题和表达结构进行系统复盘。",
				List.of("已建立基础回答意识"),
				List.of("关键点覆盖不足"),
				List.of("补强高频场景题"),
				List.of(new InterviewQuestionReportView(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", 55, "回答覆盖度不足。", null)),
				null
		);

		InterviewReportView enriched = service.enrichReport(
				baseReport,
				List.of(new InterviewQuestionSnapshot(1, "Redis", "请说明 Redis 的使用场景和一致性策略。", "PRESET", 1)),
				List.of(new InterviewRoundRecord("r1", 1, 0, "QUESTION", "题目", null, 0L, 55, "我们主要用 Redis 做缓存。", null, "TEXT", "2026-04-11T00:00:00Z", "2026-04-11T00:00:10Z", "缺少关键点：一致性策略", "FOLLOW_UP", "缺少关键点：一致性策略", List.of("一致性策略")))
		);

		assertThat(enriched.overallExplanation().generatedBy()).isEqualTo("RULE");
		assertThat(enriched.questionReports().get(0).explanation().generatedBy()).isEqualTo("RULE");
	}
```

Append this test to `voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java`:

```java
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;

	@Test
	void should_polish_report_explanation_via_streaming_responses() throws Exception {
		String responseJson = """
				{"summaryText":"整体基础可用，但关键点覆盖度仍不稳定。","evidencePoints":["多题存在关键点缺失"],"improvementSuggestions":["按题型整理固定回答框架"]}
				""";
		try (StubAiGateway gateway = new StubAiGateway(responseJson)) {
			OpenAiCompatibleAiService service = createService(gateway.baseUrl());

			InterviewReportExplanationResult result = service.polishInterviewReportExplanation(
					new InterviewReportExplanationCommand(
							"OVERALL",
							"模拟面试报告",
							"总体表现解释",
							"MEDIUM",
							"整体基础可用，但关键场景的深度和取舍表达还需要继续补强。",
							List.of("多题存在关键点缺失"),
							List.of("按题型整理固定回答框架")
					)
			);

			assertThat(result.summaryText()).contains("关键点覆盖度");
			assertThat(result.evidencePoints()).containsExactly("多题存在关键点缺失");
			assertThat(result.improvementSuggestions()).containsExactly("按题型整理固定回答框架");
			assertThat(gateway.lastResponsesRequestBody()).contains("scope: OVERALL");
			assertThat(gateway.lastResponsesRequestBody()).contains("summaryText: 整体基础可用");
		}
	}
```

Append this test to `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`:

```java
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.ai.service.langchain4j.InterviewReportExplanationAssistant;

	@Test
	void should_map_report_explanation_json_and_record_metrics() {
		InterviewReportExplanationAssistant assistant = mock(InterviewReportExplanationAssistant.class);
		when(assistant.polish(
				"QUESTION",
				"Redis",
				"请说明 Redis 的使用场景和一致性策略。",
				"MEDIUM",
				"这题核心覆盖不完整，尤其是一致性策略没有展开。",
				List.of("缺少关键点：一致性策略"),
				List.of("补充一致性策略，再说明你的实际方案和取舍。")
		)).thenReturn("""
				{
				  "summaryText": "这题回答到了 Redis 的主线，但一致性策略没有展开清楚。",
				  "evidencePoints": ["缺少关键点：一致性策略"],
				  "improvementSuggestions": ["补充一致性策略，再说明你的实际方案和取舍。"]
				}
				""");

		ProviderMetricsService metricsService = new ProviderMetricsService();
		LangChain4jAiService service = createService(
				metricsService,
				mock(InterviewReplyAssistant.class),
				mock(ResumeKeywordAssistant.class),
				mock(ResumeQuestionAssistant.class),
				assistant
		);

		InterviewReportExplanationResult result = service.polishInterviewReportExplanation(
				new InterviewReportExplanationCommand(
						"QUESTION",
						"Redis",
						"请说明 Redis 的使用场景和一致性策略。",
						"MEDIUM",
						"这题核心覆盖不完整，尤其是一致性策略没有展开。",
						List.of("缺少关键点：一致性策略"),
						List.of("补充一致性策略，再说明你的实际方案和取舍。")
				)
		);

		assertThat(result.summaryText()).contains("一致性策略");
		assertThat(result.evidencePoints()).containsExactly("缺少关键点：一致性策略");
		assertThat(result.improvementSuggestions()).containsExactly("补充一致性策略，再说明你的实际方案和取舍。");
		ProviderMetricView metric = findMetric(metricsService, "AI_REPORT_EXPLANATION");
		assertThat(metric.provider()).isEqualTo("langchain4j");
		assertThat(metric.totalCalls()).isEqualTo(1);
		verify(assistant).polish(
				"QUESTION",
				"Redis",
				"请说明 Redis 的使用场景和一致性策略。",
				"MEDIUM",
				"这题核心覆盖不完整，尤其是一致性策略没有展开。",
				List.of("缺少关键点：一致性策略"),
				List.of("补充一致性策略，再说明你的实际方案和取舍。")
		);
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest,OpenAiCompatibleAiServiceTest,LangChain4jAiServiceTest" test
```

Expected: FAIL because the AI polishing contract does not exist, providers do not implement it, and the explanation service still returns rule-only text.

- [ ] **Step 3: Add the AI polishing contract and provider implementations**

Create `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationCommand.java`:

```java
package com.interview.module.ai.service;

import java.util.List;

public record InterviewReportExplanationCommand(
		String scope,
		String title,
		String prompt,
		String level,
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions
) {
	public InterviewReportExplanationCommand {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
	}
}
```

Create `voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationResult.java`:

```java
package com.interview.module.ai.service;

import java.util.List;

public record InterviewReportExplanationResult(
		String summaryText,
		List<String> evidencePoints,
		List<String> improvementSuggestions
) {
	public InterviewReportExplanationResult {
		evidencePoints = evidencePoints == null ? List.of() : List.copyOf(evidencePoints);
		improvementSuggestions = improvementSuggestions == null ? List.of() : List.copyOf(improvementSuggestions);
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java`:

```java
package com.interview.module.ai.service;

import java.util.List;

import com.interview.module.interview.service.AnswerEvidence;
import com.interview.module.interview.service.InterviewAnswerAnalyzer;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;

public interface AiService {

	AiReply generateInterviewReply(InterviewReplyCommand command);

	ResumeKeywordExtractionResult extractResumeKeywords(String resumeText);

	List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command);

	default InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
		return null;
	}

	default AnswerEvidence analyzeInterviewAnswer(
			String question,
			String answer,
			List<String> expectedPoints
	) {
		return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
	}
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java`:

```java
	private static final String REPORT_EXPLANATION_SYSTEM_PROMPT = """
			你是模拟面试报告润色助手。
			只允许润色表达，不允许改变 level、结论、证据边界和建议方向。
			返回 JSON：
			{
			  "summaryText": "string",
			  "evidencePoints": ["string"],
			  "improvementSuggestions": ["string"]
			}
			不要增加输入中不存在的新事实。
			""";

	@Override
	public InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
		return providerMetricsService.record("AI_REPORT_EXPLANATION", "openai", () -> {
			requireApiKey();
			String content = invokeTextCompletion(
					REPORT_EXPLANATION_SYSTEM_PROMPT,
					buildReportExplanationUserContent(command),
					true
			);
			JsonNode result = objectMapper.readTree(content);
			return new InterviewReportExplanationResult(
					result.path("summaryText").asText(command == null ? "" : command.summaryText()),
					jsonArrayToList(result.path("evidencePoints")),
					jsonArrayToList(result.path("improvementSuggestions"))
			);
		});
	}

	private String buildReportExplanationUserContent(InterviewReportExplanationCommand command) {
		if (command == null) {
			return "";
		}
		return """
				scope: %s
				title: %s
				prompt: %s
				level: %s
				summaryText: %s
				evidencePoints: %s
				improvementSuggestions: %s
				""".formatted(
				valueOrEmpty(command.scope()),
				valueOrEmpty(command.title()),
				valueOrEmpty(command.prompt()),
				valueOrEmpty(command.level()),
				valueOrEmpty(command.summaryText()),
				command.evidencePoints().isEmpty() ? "(none)" : String.join("；", command.evidencePoints()),
				command.improvementSuggestions().isEmpty() ? "(none)" : String.join("；", command.improvementSuggestions())
		);
	}
```

Create `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReportExplanationAssistant.java`:

```java
package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewReportExplanationAssistant {

	@SystemMessage("""
			你是模拟面试报告润色助手。
			你只能润色表达，不能改写结论、证据和建议方向。
			只返回符合 InterviewReportExplanationResult 的 JSON。
			""")
	@UserMessage("""
			scope: {{scope}}
			title: {{title}}
			prompt: {{prompt}}
			level: {{level}}
			summaryText: {{summaryText}}
			evidencePoints: {{evidencePoints}}
			improvementSuggestions: {{improvementSuggestions}}
			""")
	String polish(
			@V("scope") String scope,
			@V("title") String title,
			@V("prompt") String prompt,
			@V("level") String level,
			@V("summaryText") String summaryText,
			@V("evidencePoints") List<String> evidencePoints,
			@V("improvementSuggestions") List<String> improvementSuggestions
	);
}
```

Update `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java`:

```java
	private final InterviewReportExplanationAssistant interviewReportExplanationAssistant;

	public LangChain4jAssistantFactory(
			OpenAiProperties openAiProperties,
			LangChain4jAiProperties langChain4jAiProperties
	) {
		OpenAiChatModel chatModel = createChatModel(openAiProperties, langChain4jAiProperties);
		this.interviewReplyAssistant = AiServices.create(InterviewReplyAssistant.class, chatModel);
		this.resumeKeywordAssistant = AiServices.create(ResumeKeywordAssistant.class, chatModel);
		this.resumeQuestionAssistant = AiServices.create(ResumeQuestionAssistant.class, chatModel);
		this.interviewReportExplanationAssistant = AiServices.create(InterviewReportExplanationAssistant.class, chatModel);
	}

	public InterviewReportExplanationAssistant interviewReportExplanationAssistant() {
		return interviewReportExplanationAssistant;
	}
```

Update `voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java`:

```java
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;

	private final InterviewReportExplanationAssistant interviewReportExplanationAssistant;

	public LangChain4jAiService(
			LangChain4jAssistantFactory assistantFactory,
			ProviderMetricsService providerMetricsService,
			ObjectMapper objectMapper
	) {
		this.interviewReplyAssistant = assistantFactory.interviewReplyAssistant();
		this.resumeKeywordAssistant = assistantFactory.resumeKeywordAssistant();
		this.resumeQuestionAssistant = assistantFactory.resumeQuestionAssistant();
		this.interviewReportExplanationAssistant = assistantFactory.interviewReportExplanationAssistant();
		this.providerMetricsService = providerMetricsService;
		this.objectMapper = objectMapper;
	}

	@Override
	public InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
		return providerMetricsService.record("AI_REPORT_EXPLANATION", PROVIDER, () -> {
			String content = interviewReportExplanationAssistant.polish(
					command == null ? "" : command.scope(),
					command == null ? "" : command.title(),
					command == null ? "" : command.prompt(),
					command == null ? "" : command.level(),
					command == null ? "" : command.summaryText(),
					command == null ? List.of() : command.evidencePoints(),
					command == null ? List.of() : command.improvementSuggestions()
			);
			InterviewReportExplanationResult result = parseJsonObject(content, InterviewReportExplanationResult.class);
			if (result == null) {
				return null;
			}
			return new InterviewReportExplanationResult(
					firstNonBlank(result.summaryText(), command == null ? "" : command.summaryText()),
					normalizeList(result.evidencePoints()),
					normalizeList(result.improvementSuggestions())
			);
		});
	}
```

- [ ] **Step 4: Add service-level fallback and merge polished copy only on success**

Starting from the Task 1 class, replace the constructor and `enrichReport(...)`, then append these polishing helpers below the existing rule-generation methods in `voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java`:

```java
package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.interview.module.ai.service.AiService;
import com.interview.module.ai.service.InterviewReportExplanationCommand;
import com.interview.module.ai.service.InterviewReportExplanationResult;
import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

public class InterviewReportExplanationService {

	private final AiService aiService;

	public InterviewReportExplanationService() {
		this(null);
	}

	public InterviewReportExplanationService(AiService aiService) {
		this.aiService = aiService;
	}

	public InterviewReportView enrichReport(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		List<InterviewQuestionReportView> explainedQuestions = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(report)) {
			InterviewQuestionSnapshot question = findQuestion(questions, questionReport.questionIndex());
			InterviewQuestionExplanationView ruleExplanation = buildQuestionExplanation(
					question,
					questionReport,
					roundsForQuestion(rounds, questionReport.questionIndex())
			);
			InterviewQuestionExplanationView finalExplanation = polishQuestionExplanation(questionReport, ruleExplanation);
			explainedQuestions.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					finalExplanation
			));
		}

		InterviewOverallExplanationView ruleOverallExplanation = buildOverallExplanation(
				report.overallScore(),
				explainedQuestions,
				rounds
		);
		InterviewOverallExplanationView finalOverallExplanation = polishOverallExplanation(report, ruleOverallExplanation);

		return new InterviewReportView(
				report.sessionId(),
				report.status(),
				report.title(),
				report.overallScore(),
				report.overallComment(),
				report.strengths(),
				report.weaknesses(),
				report.suggestions(),
				List.copyOf(explainedQuestions),
				finalOverallExplanation
		);
	}

	private InterviewOverallExplanationView polishOverallExplanation(
			InterviewReportView report,
			InterviewOverallExplanationView ruleExplanation
	) {
		InterviewReportExplanationResult polished = safePolish(new InterviewReportExplanationCommand(
				"OVERALL",
				report.title(),
				"总体表现解释",
				ruleExplanation.level(),
				ruleExplanation.summaryText(),
				ruleExplanation.evidencePoints(),
				ruleExplanation.improvementSuggestions()
		));
		if (!hasPolishedContent(polished)) {
			return ruleExplanation;
		}
		return new InterviewOverallExplanationView(
				ruleExplanation.level(),
				firstNonBlank(polished.summaryText(), ruleExplanation.summaryText()),
				preferList(polished.evidencePoints(), ruleExplanation.evidencePoints()),
				preferList(polished.improvementSuggestions(), ruleExplanation.improvementSuggestions()),
				"RULE_PLUS_LLM"
		);
	}

	private InterviewQuestionExplanationView polishQuestionExplanation(
			InterviewQuestionReportView questionReport,
			InterviewQuestionExplanationView ruleExplanation
	) {
		InterviewReportExplanationResult polished = safePolish(new InterviewReportExplanationCommand(
				"QUESTION",
				questionReport.title(),
				questionReport.prompt(),
				ruleExplanation.performanceLevel(),
				ruleExplanation.summaryText(),
				ruleExplanation.evidencePoints(),
				List.of(ruleExplanation.improvementSuggestion())
		));
		if (!hasPolishedContent(polished)) {
			return ruleExplanation;
		}
		List<String> suggestions = preferList(polished.improvementSuggestions(), List.of(ruleExplanation.improvementSuggestion()));
		return new InterviewQuestionExplanationView(
				ruleExplanation.performanceLevel(),
				firstNonBlank(polished.summaryText(), ruleExplanation.summaryText()),
				preferList(polished.evidencePoints(), ruleExplanation.evidencePoints()),
				suggestions.get(0),
				"RULE_PLUS_LLM"
		);
	}

	private InterviewReportExplanationResult safePolish(InterviewReportExplanationCommand command) {
		if (aiService == null || command == null) {
			return null;
		}
		try {
			return aiService.polishInterviewReportExplanation(command);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private boolean hasPolishedContent(InterviewReportExplanationResult result) {
		if (result == null) {
			return false;
		}
		return result.summaryText() != null && !result.summaryText().isBlank();
	}

	private String firstNonBlank(String preferred, String fallback) {
		if (preferred != null && !preferred.isBlank()) {
			return preferred.trim();
		}
		return fallback;
	}

	private List<String> preferList(List<String> preferred, List<String> fallback) {
		List<String> normalized = new ArrayList<>();
		for (String item : preferred == null ? List.<String>of() : preferred) {
			if (item != null && !item.isBlank()) {
				normalized.add(item.trim());
			}
		}
		if (!normalized.isEmpty()) {
			return List.copyOf(normalized);
		}
		return fallback == null ? List.of() : List.copyOf(fallback);
	}
}
```

Update the constructor assignment in `voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java`:

```java
		this.interviewReportExplanationService = new InterviewReportExplanationService(aiService);
```

Update the factory helper in `voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java`:

```java
	private LangChain4jAiService createService(
			ProviderMetricsService metricsService,
			InterviewReplyAssistant interviewReplyAssistant,
			ResumeKeywordAssistant resumeKeywordAssistant,
			ResumeQuestionAssistant resumeQuestionAssistant,
			InterviewReportExplanationAssistant interviewReportExplanationAssistant
	) {
		LangChain4jAssistantFactory factory = mock(LangChain4jAssistantFactory.class);
		when(factory.interviewReplyAssistant()).thenReturn(interviewReplyAssistant);
		when(factory.resumeKeywordAssistant()).thenReturn(resumeKeywordAssistant);
		when(factory.resumeQuestionAssistant()).thenReturn(resumeQuestionAssistant);
		when(factory.interviewReportExplanationAssistant()).thenReturn(interviewReportExplanationAssistant);
		return new LangChain4jAiService(factory, metricsService, new ObjectMapper());
	}
```

Update the four existing `createService(...)` calls earlier in the same test file so they all pass a fifth argument:

```java
		LangChain4jAiService service = createService(
				metricsService,
				assistant,
				mock(ResumeKeywordAssistant.class),
				mock(ResumeQuestionAssistant.class),
				mock(InterviewReportExplanationAssistant.class)
		);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run in `voice-interview-backend`:

```bash
mvn -q "-Dtest=InterviewReportExplanationServiceTest,OpenAiCompatibleAiServiceTest,LangChain4jAiServiceTest" test
```

Expected: PASS. The explanation service should prefer polished copy only when valid JSON arrives, and otherwise keep the rule text. Both AI providers should expose the same contract.

- [ ] **Step 6: Commit**

```bash
git add voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationCommand.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/InterviewReportExplanationResult.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/AiService.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/OpenAiCompatibleAiService.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/InterviewReportExplanationAssistant.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAssistantFactory.java \
        voice-interview-backend/src/main/java/com/interview/module/ai/service/langchain4j/LangChain4jAiService.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java \
        voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java \
        voice-interview-backend/src/test/java/com/interview/module/ai/service/OpenAiCompatibleAiServiceTest.java \
        voice-interview-backend/src/test/java/com/interview/module/ai/service/LangChain4jAiServiceTest.java \
        voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java
git commit -m "feat(ai): 增加报告解释润色链路"
```

---

### Task 3: Extend Mobile Types And Render Diagnostic Explanation Cards

**Files:**
- Modify: `voice-interview-mobile/src/types/interview.ts`
- Modify: `voice-interview-mobile/src/pages/interview/report.vue`
- Test: `voice-interview-mobile/src/pages/interview/report.vue`
- Test: `voice-interview-mobile/src/types/interview.ts`

- [ ] **Step 1: Write the failing report-page bindings**

Update the template and script references in `voice-interview-mobile/src/pages/interview/report.vue` so the page expects `overallExplanation`, per-question `explanation`, and explanation source labels before the helper code exists:

```vue
    <view v-if="overallExplanation" class="section-card explanation-card">
      <view class="section-head">
        <text class="section-title">为什么是这个结论</text>
        <text class="explanation-chip">{{ explanationSourceLabel(overallExplanation.generatedBy) }}</text>
      </view>
      <text class="diagnosis-level" :class="toneClass(overallExplanation.level)">
        {{ levelLabel(overallExplanation.level) }}
      </text>
      <text class="bullet-copy emphasis">{{ overallExplanation.summaryText }}</text>
      <view v-for="point in overallExplanation.evidencePoints" :key="point" class="bullet-card compact-card">
        <text class="bullet-title">证据点</text>
        <text class="bullet-copy">{{ point }}</text>
      </view>
      <view v-for="item in overallExplanation.improvementSuggestions" :key="item" class="bullet-card compact-card">
        <text class="bullet-title">改进建议</text>
        <text class="bullet-copy">{{ item }}</text>
      </view>
    </view>
```

And change the question detail block to:

```vue
    <view class="section-card">
      <text class="section-title">分题回顾</text>
      <view v-for="item in report?.questionReports || []" :key="item.questionIndex" class="bullet-card question-card">
        <text class="bullet-title">第 {{ item.questionIndex }} 题 · {{ item.title }}</text>
        <text class="bullet-copy">得分：{{ item.score ?? '--' }}</text>
        <text class="bullet-copy">{{ item.summary }}</text>
        <view v-if="questionExplanation(item)" class="question-explanation">
          <view class="section-head compact-head">
            <text class="bullet-title">诊断解释</text>
            <text class="explanation-chip">{{ explanationSourceLabel(questionExplanation(item)?.generatedBy) }}</text>
          </view>
          <text class="diagnosis-level" :class="toneClass(questionExplanation(item)?.performanceLevel)">
            {{ levelLabel(questionExplanation(item)?.performanceLevel) }}
          </text>
          <text class="bullet-copy emphasis">{{ questionExplanation(item)?.summaryText }}</text>
          <view v-for="point in questionExplanation(item)?.evidencePoints || []" :key="point" class="bullet-card compact-card">
            <text class="bullet-title">证据点</text>
            <text class="bullet-copy">{{ point }}</text>
          </view>
          <view class="bullet-card compact-card">
            <text class="bullet-title">改进建议</text>
            <text class="bullet-copy">{{ questionExplanation(item)?.improvementSuggestion }}</text>
          </view>
        </view>
      </view>
    </view>
```

- [ ] **Step 2: Run the type checker to verify it fails**

Run in `voice-interview-mobile`:

```bash
npm run type-check
```

Expected: FAIL because `InterviewReport` and `InterviewQuestionReport` do not yet declare explanation fields, and `overallExplanation` / `questionExplanation` / `explanationSourceLabel` / `toneClass` / `levelLabel` are not implemented in the page script.

- [ ] **Step 3: Implement mobile types, helper logic, and styles**

Update `voice-interview-mobile/src/types/interview.ts`:

```ts
export type ExplanationGeneratedBy = 'RULE' | 'RULE_PLUS_LLM'

export interface InterviewOverallExplanation {
  level: 'STRONG' | 'MEDIUM' | 'WEAK'
  summaryText: string
  evidencePoints: string[]
  improvementSuggestions: string[]
  generatedBy: ExplanationGeneratedBy
}

export interface InterviewQuestionExplanation {
  performanceLevel: 'STRONG' | 'MEDIUM' | 'WEAK'
  summaryText: string
  evidencePoints: string[]
  improvementSuggestion: string
  generatedBy: ExplanationGeneratedBy
}

export interface InterviewQuestionReport {
  questionIndex: number
  title: string
  prompt: string
  score: number | null
  summary: string
  explanation?: InterviewQuestionExplanation | null
}

export interface InterviewReport {
  sessionId: string
  status: string
  title: string
  overallScore: number | null
  overallComment: string
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  questionReports: InterviewQuestionReport[]
  overallExplanation?: InterviewOverallExplanation | null
}
```

Update the script section of `voice-interview-mobile/src/pages/interview/report.vue`:

```ts
<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { API_BASE_URL } from '@/config/api'
import { getInterviewReport } from '@/services/interviewApi'
import { useUserStore } from '@/stores/user'
import type {
  InterviewQuestionExplanation,
  InterviewQuestionReport,
  InterviewReport,
} from '@/types/interview'
import { ensureAuthenticated } from '@/utils/auth'

const report = ref<InterviewReport | null>(null)
const missingSession = ref(false)
const userStore = useUserStore()

const overallExplanation = computed(() => report.value?.overallExplanation ?? null)

const questionExplanation = (item: InterviewQuestionReport): InterviewQuestionExplanation | null =>
  item.explanation ?? null

const explanationSourceLabel = (generatedBy?: string | null) =>
  generatedBy === 'RULE_PLUS_LLM' ? 'AI 润色' : '规则生成'

const levelLabel = (level?: string | null) => {
  if (level === 'STRONG') return '表现较强'
  if (level === 'WEAK') return '需要补强'
  return '基础可用'
}

const toneClass = (level?: string | null) => {
  if (level === 'STRONG') return 'tone-strong'
  if (level === 'WEAK') return 'tone-weak'
  return 'tone-medium'
}

onLoad(async (query) => {
  if (!ensureAuthenticated(userStore, typeof query?.sessionId === 'string' ? `/pages/interview/report?sessionId=${query.sessionId}` : '/pages/interview/report')) {
    return
  }
  const sessionId = typeof query?.sessionId === 'string' ? query.sessionId : ''
  if (!sessionId) {
    missingSession.value = true
    uni.showToast({
      title: '缺少 sessionId，先去完成一场面试',
      icon: 'none',
      duration: 2200,
    })
    return
  }

  try {
    const payload = await getInterviewReport(API_BASE_URL, sessionId)
    if (!payload.success) {
      throw new Error(payload.message || '获取报告失败')
    }
    report.value = payload.data
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '获取报告失败',
      icon: 'none',
      duration: 2200,
    })
  }
})

const goToSession = () => {
  uni.navigateTo({
    url: '/pages/interview/setup',
  })
}

const goToHistory = () => {
  uni.navigateTo({
    url: '/pages/history/list',
  })
}

const goHome = () => {
  uni.reLaunch({
    url: '/pages/index/index',
  })
}
</script>
```

Update the style section of `voice-interview-mobile/src/pages/interview/report.vue`:

```css
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.compact-head {
  margin-bottom: 8rpx;
}

.explanation-card,
.question-card,
.question-explanation {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.explanation-chip {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  color: var(--studio-text);
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.diagnosis-level {
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.tone-strong {
  color: #7bd88f;
}

.tone-medium {
  color: #f4c15d;
}

.tone-weak {
  color: #ff8f8f;
}

.compact-card {
  margin-bottom: 0;
}

.emphasis {
  color: var(--studio-text);
}
```

- [ ] **Step 4: Run the type checker to verify it passes**

Run in `voice-interview-mobile`:

```bash
npm run type-check
```

Expected: PASS. The page should now compile with the new backend report shape while still tolerating missing explanation fields through optional chaining.

- [ ] **Step 5: Run the H5 build to verify the page bundles**

Run in `voice-interview-mobile`:

```bash
npm run build:h5
```

Expected: PASS. The report page should bundle without template or style errors.

- [ ] **Step 6: Commit**

```bash
git add voice-interview-mobile/src/types/interview.ts \
        voice-interview-mobile/src/pages/interview/report.vue
git commit -m "feat(mobile): 展示报告解释卡片"
```

---

### Self-Review

**Spec coverage**
- [ ] Requirement `总评 + 分题回顾都增强` is covered by Task 1 DTO expansion and Task 3 mobile rendering.
- [ ] Requirement `规则打底 + LLM 润色` is covered by Task 1 rule service and Task 2 provider contract.
- [ ] Requirement `LLM 失败回退规则文案` is covered by Task 2 service fallback tests and `safePolish(...)`.
- [ ] Requirement `只改 voice-interview-mobile 报告页` is covered by Task 3 only touching `voice-interview-mobile/src/pages/interview/report.vue` and `src/types/interview.ts`.
- [ ] Requirement `不改接口路径、不改 schema` is enforced by the File Structure section and `Do Not Change In This Plan`.

**Placeholder scan**
- [ ] Run:

```bash
rg -n "TODO|TBD|implement later|fill in details|Similar to Task|appropriate error handling" docs/superpowers/plans/2026-04-11-report-explanation-implementation.md | rg -v "^[0-9]+:rg -n"
```

Expected: no output

- [ ] Run:

```bash
rg -n "overallExplanation|questionReports\\(\\)|polishInterviewReportExplanation|InterviewReportExplanationCommand|InterviewReportExplanationResult" docs/superpowers/plans/2026-04-11-report-explanation-implementation.md | rg -v "^[0-9]+:rg -n"
```

Expected: output only for the final names reused consistently across Tasks 1-3.

**Final verification checklist before implementation handoff**
- [ ] Backend rule-only verification:

```bash
cd voice-interview-backend
mvn -q "-Dtest=InterviewReportExplanationServiceTest,SimpleInterviewEngineIntegrationTest#should_include_rule_explanations_in_report_view" test
```

Expected: PASS

- [ ] Backend AI-polish verification:

```bash
cd voice-interview-backend
mvn -q "-Dtest=InterviewReportExplanationServiceTest,OpenAiCompatibleAiServiceTest,LangChain4jAiServiceTest" test
```

Expected: PASS

- [ ] Mobile verification:

```bash
cd voice-interview-mobile
npm run type-check
npm run build:h5
```

Expected: PASS
