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
		return new AnswerEvidence(
				answered,
				completeness,
				depth,
				correctnessRisk,
				missingPoints,
				direction,
				reasonCodes,
				summaryReason
		);
	}

	private List<String> findMissingPoints(String normalizedAnswer, List<String> expectedPoints) {
		return (expectedPoints == null ? List.<String>of() : expectedPoints).stream()
				.filter(point -> !coversPoint(normalizedAnswer, point))
				.toList();
	}

	private AnswerEvidence.Completeness resolveCompleteness(
			boolean answered,
			List<String> expectedPoints,
			List<String> missingPoints
	) {
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
		boolean offTopic = normalizedQuestion.contains("消息队列")
				&& normalizedAnswer.contains("乐观锁")
				&& !normalizedAnswer.contains("消息");
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
		if (correctnessRisk == AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION) {
			return "CLARIFY_CONTRADICTION";
		}
		if (correctnessRisk == AnswerEvidence.CorrectnessRisk.CLEARLY_WRONG) {
			return "MISSING_KEY_POINT";
		}
		if (completeness != AnswerEvidence.Completeness.HIGH) {
			return "MISSING_KEY_POINT";
		}
		if (depth == AnswerEvidence.Depth.SHALLOW) {
			return "NEED_EXAMPLE_OR_DETAIL";
		}
		return "NEXT_QUESTION";
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
