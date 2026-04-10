package com.interview.module.interview.resume;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.interview.module.library.service.LibraryCategory;
import com.interview.module.library.service.LibraryQuestion;

@Component
public class ResumeQuestionMatcher {

	private static final Map<String, List<String>> KEYWORD_ALIASES = Map.ofEntries(
			Map.entry("Java", List.of("java", "jdk", "jvm")),
			Map.entry("Spring", List.of("spring", "spring framework")),
			Map.entry("Spring Boot", List.of("springboot", "spring boot")),
			Map.entry("Spring Cloud", List.of("springcloud", "spring cloud")),
			Map.entry("MySQL", List.of("mysql")),
			Map.entry("Redis", List.of("redis")),
			Map.entry("Kafka", List.of("kafka")),
			Map.entry("RabbitMQ", List.of("rabbitmq", "rabbit mq")),
			Map.entry("Elasticsearch", List.of("elasticsearch", "elastic search")),
			Map.entry("MongoDB", List.of("mongodb", "mongo db")),
			Map.entry("Docker", List.of("docker")),
			Map.entry("Kubernetes", List.of("k8s", "kubernetes")),
			Map.entry("Linux", List.of("linux")),
			Map.entry("Netty", List.of("netty")),
			Map.entry("gRPC", List.of("grpc")),
			Map.entry("Prometheus", List.of("prometheus")),
			Map.entry("Nginx", List.of("nginx")),
			Map.entry("微服务", List.of("微服务", "microservice", "microservices")),
			Map.entry("高并发", List.of("高并发", "并发", "highconcurrency")),
			Map.entry("分布式", List.of("分布式", "distributed")),
			Map.entry("消息队列", List.of("消息队列", "messagequeue"))
	);

	public List<String> normalizeKeywords(List<String> rawKeywords) {
		if (rawKeywords == null || rawKeywords.isEmpty()) {
			return List.of();
		}
		Set<String> unique = new LinkedHashSet<>();
		for (String raw : rawKeywords) {
			if (!StringUtils.hasText(raw)) continue;
			String canonical = toCanonicalKeyword(raw);
			unique.add(canonical == null ? raw.trim() : canonical);
		}
		return List.copyOf(unique);
	}

	public List<String> extractKeywordsFromTextFallback(String resumeText) {
		if (!StringUtils.hasText(resumeText)) return List.of();
		String normalized = normalizeToken(resumeText);
		Set<String> matched = new LinkedHashSet<>();
		for (Map.Entry<String, List<String>> entry : KEYWORD_ALIASES.entrySet()) {
			if (containsAnyAlias(normalized, entry.getKey())) {
				matched.add(entry.getKey());
			}
		}
		return List.copyOf(matched);
	}

	public MatchResult match(List<String> rawKeywords, List<LibraryCategory> categories,
			List<LibraryQuestion> questions, int limit) {
		List<String> keywords = normalizeKeywords(rawKeywords);
		if (keywords.isEmpty() || questions == null || questions.isEmpty() || limit <= 0) {
			return new MatchResult(keywords, List.of(), List.of(), keywords);
		}

		Map<String, String> categoryNameById = new LinkedHashMap<>();
		if (categories != null) {
			for (LibraryCategory c : categories) categoryNameById.put(c.id(), c.name());
		}

		List<ScoredQuestion> scored = new ArrayList<>();
		for (LibraryQuestion q : questions) {
			String catName = categoryNameById.getOrDefault(q.categoryId(), "");
			ScoreDetails details = scoreQuestion(q, catName, keywords);
			if (details.score() > 0) scored.add(new ScoredQuestion(q, catName, details.score(), details.matchedKeywords()));
		}

		scored.sort(Comparator.comparingInt(ScoredQuestion::score).reversed()
				.thenComparing(sq -> safe(sq.question().title())));

		List<LibraryQuestion> matchedQuestions = new ArrayList<>();
		Set<String> matchedCategoryNames = new LinkedHashSet<>();
		Set<String> coveredKeywords = new LinkedHashSet<>();
		Set<String> dedupeKeys = new LinkedHashSet<>();

		for (ScoredQuestion sq : scored) {
			String dedupeKey = normalizeToken(sq.question().title() + "::" + sq.question().content());
			if (!dedupeKeys.add(dedupeKey)) continue;
			matchedQuestions.add(sq.question());
			if (StringUtils.hasText(sq.categoryName())) matchedCategoryNames.add(sq.categoryName().trim());
			coveredKeywords.addAll(sq.matchedKeywords());
			if (matchedQuestions.size() >= limit) break;
		}

		List<String> missing = keywords.stream().filter(k -> !coveredKeywords.contains(k)).toList();
		return new MatchResult(keywords, List.copyOf(matchedCategoryNames), List.copyOf(matchedQuestions), missing);
	}

	private ScoreDetails scoreQuestion(LibraryQuestion q, String categoryName, List<String> keywords) {
		String normTitle = normalizeToken(q.title());
		String normContent = normalizeToken(q.content());
		String normCat = normalizeToken(categoryName);

		int score = 0;
		Set<String> matched = new LinkedHashSet<>();
		for (String kw : keywords) {
			boolean titleHit = containsAnyAlias(normTitle, kw);
			boolean contentHit = containsAnyAlias(normContent, kw);
			boolean catHit = containsAnyAlias(normCat, kw);
			if (titleHit) score += 8;
			if (contentHit) score += 5;
			if (catHit) score += 3;
			if (titleHit || contentHit || catHit) matched.add(kw);
		}
		if (!matched.isEmpty()) {
			int diff = q.difficulty() == null ? 1 : q.difficulty();
			score += Math.max(0, 3 - Math.abs(diff - 2));
		}
		return new ScoreDetails(score, matched);
	}

	private boolean containsAnyAlias(String normalizedText, String keyword) {
		if (!StringUtils.hasText(normalizedText) || !StringUtils.hasText(keyword)) return false;
		String canonical = toCanonicalKeyword(keyword);
		if (canonical == null) return normalizedText.contains(normalizeToken(keyword));
		for (String alias : KEYWORD_ALIASES.getOrDefault(canonical, List.of())) {
			if (normalizedText.contains(normalizeToken(alias))) return true;
		}
		return normalizedText.contains(normalizeToken(canonical));
	}

	private String toCanonicalKeyword(String keyword) {
		String norm = normalizeToken(keyword);
		for (Map.Entry<String, List<String>> entry : KEYWORD_ALIASES.entrySet()) {
			if (normalizeToken(entry.getKey()).equals(norm)) return entry.getKey();
			for (String alias : entry.getValue()) {
				if (normalizeToken(alias).equals(norm)) return entry.getKey();
			}
		}
		return null;
	}

	private String normalizeToken(String value) {
		if (!StringUtils.hasText(value)) return "";
		return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-./]+", "");
	}

	private String safe(String value) { return value == null ? "" : value; }

	public record MatchResult(List<String> normalizedKeywords, List<String> matchedCategoryNames,
			List<LibraryQuestion> matchedQuestions, List<String> missingKeywords) {}

	private record ScoreDetails(int score, Set<String> matchedKeywords) {}
	private record ScoredQuestion(LibraryQuestion question, String categoryName, int score, Set<String> matchedKeywords) {}
}
