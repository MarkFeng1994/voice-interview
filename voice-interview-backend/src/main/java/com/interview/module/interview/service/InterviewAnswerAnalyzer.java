package com.interview.module.interview.service;

import java.util.List;
import java.util.Locale;

public class InterviewAnswerAnalyzer {

	public static InterviewAnswerAnalyzer heuristic() {
		return new InterviewAnswerAnalyzer();
	}

	public Analysis analyze(String question, String answer, List<String> expectedPoints) {
		String normalizedAnswer = normalize(answer);
		boolean answered = !normalizedAnswer.isBlank();
		List<String> missingPoints = (expectedPoints == null ? List.<String>of() : expectedPoints).stream()
				.filter(point -> !coversPoint(normalizedAnswer, point))
				.toList();
		return new Analysis(
				answered,
				missingPoints.isEmpty() ? "HIGH" : "MEDIUM",
				missingPoints,
				!missingPoints.isEmpty(),
				missingPoints.isEmpty() ? "NEXT_QUESTION" : "MISSING_KEY_POINT",
				missingPoints.isEmpty() ? "回答完整" : "关键点缺失"
		);
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

	public record Analysis(
			boolean answered,
			String completeness,
			List<String> missingPoints,
			boolean followUpNeeded,
			String followUpDirection,
			String reason
	) {
	}
}
