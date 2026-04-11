package com.interview.module.interview.engine.store;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.entity.RoundEntity;
import com.interview.module.interview.entity.SessionEntity;
import com.interview.module.interview.entity.SessionQuestionEntity;
import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.mapper.RoundMapper;
import com.interview.module.interview.mapper.SessionMapper;
import com.interview.module.interview.mapper.SessionQuestionMapper;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

@Component
@ConditionalOnProperty(prefix = "app.interview", name = "session-store", havingValue = "jdbc")
public class JdbcInterviewSessionStore implements InterviewSessionStore {

	private final SessionMapper sessionMapper;
	private final SessionQuestionMapper sessionQuestionMapper;
	private final RoundMapper roundMapper;
	private final ObjectMapper objectMapper;
	private final int defaultDurationMinutes;

	public JdbcInterviewSessionStore(SessionMapper sessionMapper,
			SessionQuestionMapper sessionQuestionMapper,
			RoundMapper roundMapper,
			ObjectMapper objectMapper,
			@Value("${app.interview.default-duration-minutes:60}") int defaultDurationMinutes) {
		this.sessionMapper = sessionMapper;
		this.sessionQuestionMapper = sessionQuestionMapper;
		this.roundMapper = roundMapper;
		this.objectMapper = objectMapper;
		this.defaultDurationMinutes = defaultDurationMinutes;
	}

	@Override
	public void save(InterviewSessionState sessionState) {
		long sessionId = findInternalSessionId(sessionState.getSessionId())
				.orElseGet(() -> insertSession(sessionState));

		SessionEntity entity = sessionMapper.selectById(sessionId);
		entity.setUserId(parseUserId(sessionState.getOwnerUserId()));
		entity.setSessionKey(sessionState.getSessionId());
		entity.setTitle(resolveTitle(sessionState));
		entity.setConfigJson(serializeConfig(sessionState));
		entity.setStatus(sessionState.getStatus());
		entity.setCurrentQuestionIndex(sessionState.getCurrentQuestionIndex());
		entity.setTotalQuestionCount(sessionState.getQuestions().size());
		entity.setOverallScore(computeOverallScore(sessionState.getRounds()));
		entity.setLastActiveAt(LocalDateTime.now());
		sessionMapper.updateById(entity);

		roundMapper.delete(new LambdaQueryWrapper<RoundEntity>().eq(RoundEntity::getSessionId, sessionId));
		sessionQuestionMapper.delete(new LambdaQueryWrapper<SessionQuestionEntity>().eq(SessionQuestionEntity::getSessionId, sessionId));

		Map<Integer, Long> questionIdMap = insertQuestionSnapshots(sessionId, sessionState.getQuestions());
		insertRounds(sessionId, sessionState.getRounds(), questionIdMap);
	}

	@Override
	public Optional<InterviewSessionState> findById(String sessionId) {
		SessionEntity sessionEntity = sessionMapper.selectOne(
				new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionKey, sessionId));
		if (sessionEntity == null) {
			return Optional.empty();
		}

		List<SessionQuestionEntity> questionEntities = sessionQuestionMapper.selectList(
				new LambdaQueryWrapper<SessionQuestionEntity>()
						.eq(SessionQuestionEntity::getSessionId, sessionEntity.getId())
						.orderByAsc(SessionQuestionEntity::getQuestionIndex));

		List<RoundEntity> roundEntities = roundMapper.selectList(
				new LambdaQueryWrapper<RoundEntity>()
						.eq(RoundEntity::getSessionId, sessionEntity.getId())
						.orderByAsc(RoundEntity::getQuestionIndex)
						.orderByAsc(RoundEntity::getFollowUpIndex)
						.orderByAsc(RoundEntity::getId));

		List<InterviewQuestionSnapshot> questions = questionEntities.stream()
				.map(e -> new InterviewQuestionSnapshot(
						e.getQuestionIndex(),
						e.getTitleSnapshot(),
						e.getContentSnapshot(),
						e.getSourceSnapshot(),
						e.getDifficultySnapshot()
				))
				.toList();

		List<InterviewRoundRecord> rounds = roundEntities.stream().map(this::toRoundRecord).toList();

		SessionConfig config = parseConfig(sessionEntity.getConfigJson());
		InterviewSessionState state = new InterviewSessionState(
				sessionEntity.getSessionKey(),
				String.valueOf(sessionEntity.getUserId()),
				config.ownerNickname(),
				questions,
				config.stage(),
				config.durationMinutes(),
				config.maxFollowUpPerQuestion(),
				config.interviewerSpeakerId(),
				config.interviewerSpeechSpeed()
		);
		state.setStatus(sessionEntity.getStatus());
		state.setCurrentQuestionIndex(sessionEntity.getCurrentQuestionIndex());
		state.setFollowUpIndex(resolveFollowUpIndex(rounds, sessionEntity.getCurrentQuestionIndex()));
		state.getRounds().addAll(rounds);
		return Optional.of(state);
	}

	@Override
	public List<InterviewSessionState> findAll() {
		List<SessionEntity> sessionEntities = sessionMapper.selectList(
				new LambdaQueryWrapper<SessionEntity>().orderByDesc(SessionEntity::getId));
		List<InterviewSessionState> sessions = new ArrayList<>();
		for (SessionEntity e : sessionEntities) {
			findById(e.getSessionKey()).ifPresent(sessions::add);
		}
		return sessions;
	}

	private Optional<Long> findInternalSessionId(String sessionKey) {
		SessionEntity entity = sessionMapper.selectOne(
				new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionKey, sessionKey));
		return entity == null ? Optional.empty() : Optional.of(entity.getId());
	}

	private long insertSession(InterviewSessionState sessionState) {
		SessionEntity entity = new SessionEntity();
		entity.setSessionKey(sessionState.getSessionId());
		entity.setUserId(parseUserId(sessionState.getOwnerUserId()));
		entity.setTitle(resolveTitle(sessionState));
		entity.setSelectedCategoryIds("[]");
		entity.setConfigJson(serializeConfig(sessionState));
		entity.setInteractionMode("PUSH_TO_TALK");
		entity.setStatus(sessionState.getStatus());
		entity.setCurrentQuestionIndex(sessionState.getCurrentQuestionIndex());
		entity.setTotalQuestionCount(sessionState.getQuestions().size());
		entity.setOverallScore(computeOverallScore(sessionState.getRounds()));
		entity.setLastActiveAt(LocalDateTime.now());
		sessionMapper.insert(entity);
		return entity.getId();
	}

	private Map<Integer, Long> insertQuestionSnapshots(long sessionId, List<InterviewQuestionSnapshot> questions) {
		Map<Integer, Long> questionIdMap = new LinkedHashMap<>();
		for (InterviewQuestionSnapshot q : questions) {
			SessionQuestionEntity entity = new SessionQuestionEntity();
			entity.setSessionId(sessionId);
			entity.setQuestionId((long) q.questionIndex());
			entity.setQuestionIndex(q.questionIndex());
			entity.setCategoryId(0L);
			entity.setTitleSnapshot(q.titleSnapshot());
			entity.setContentSnapshot(q.promptSnapshot());
			entity.setDifficultySnapshot(normalizeDifficultySnapshot(q.difficultySnapshot()));
			entity.setSourceSnapshot(normalizeSourceSnapshot(q.sourceSnapshot()));
			sessionQuestionMapper.insert(entity);
			questionIdMap.put(q.questionIndex(), entity.getId());
		}
		return questionIdMap;
	}

	private Integer normalizeDifficultySnapshot(Integer difficultySnapshot) {
		return difficultySnapshot == null ? 1 : difficultySnapshot;
	}

	private String normalizeSourceSnapshot(String sourceSnapshot) {
		if (sourceSnapshot == null || sourceSnapshot.isBlank()) {
			return "MANUAL";
		}
		return sourceSnapshot;
	}

	private void insertRounds(long sessionId, List<InterviewRoundRecord> rounds, Map<Integer, Long> questionIdMap) {
		for (InterviewRoundRecord round : rounds) {
			RoundEntity entity = new RoundEntity();
			entity.setSessionId(sessionId);
			entity.setInterviewQuestionId(questionIdMap.getOrDefault(round.questionIndex(), 0L));
			entity.setQuestionIndex(round.questionIndex());
			entity.setFollowUpIndex(round.followUpIndex());
			entity.setRoundType(round.roundType());
			entity.setUserAnswerMode(round.userAnswerMode() == null ? "VOICE" : round.userAnswerMode());
			entity.setFinalUserAnswerText(round.userAnswerText());
			entity.setUserAudioUrl(round.userAudioUrl());
			entity.setAiMessageText(round.aiMessageText());
			entity.setAiAnalysis(serializeRoundAnalysis(round));
			entity.setTtsAudioUrl(round.aiAudioUrl());
			entity.setScore(round.scoreSuggestion());
			entity.setDurationMs(round.aiAudioDurationMs());
			entity.setCreatedAt(parseDateTime(round.createdAt()));
			entity.setAnsweredAt(parseDateTime(round.answeredAt()));
			roundMapper.insert(entity);
		}
	}

	private InterviewRoundRecord toRoundRecord(RoundEntity e) {
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
	}

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

	private Integer computeOverallScore(List<InterviewRoundRecord> rounds) {
		List<Integer> scores = rounds.stream()
				.map(InterviewRoundRecord::scoreSuggestion)
				.filter(s -> s != null)
				.toList();
		if (scores.isEmpty()) return null;
		return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
	}

	private String resolveTitle(InterviewSessionState sessionState) {
		return sessionState.getQuestions().isEmpty()
				? "模拟面试"
				: sessionState.getQuestions().get(0).titleSnapshot();
	}

	private String serializeConfig(InterviewSessionState sessionState) {
		try {
			var map = new java.util.LinkedHashMap<String, Object>();
			map.put("stage", sessionState.getStage());
			map.put("durationMinutes", sessionState.getDurationMinutes());
			map.put("maxFollowUpPerQuestion", sessionState.getMaxFollowUpPerQuestion());
			map.put("ownerNickname", sessionState.getOwnerNickname());
			map.put("interviewerSpeakerId", sessionState.getInterviewerSpeakerId());
			map.put("interviewerSpeechSpeed", sessionState.getInterviewerSpeechSpeed());
			return objectMapper.writeValueAsString(map);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize interview session config", ex);
		}
	}

	private SessionConfig parseConfig(String json) {
		try {
			return objectMapper.readValue(json, SessionConfig.class);
		} catch (JsonProcessingException ex) {
			return new SessionConfig("面试者", 2, null, 1.0, InterviewStage.OPENING.name(), defaultDurationMinutes);
		}
	}

	private int resolveFollowUpIndex(List<InterviewRoundRecord> rounds, int currentQuestionIndexZeroBased) {
		int currentQuestionIndex = currentQuestionIndexZeroBased + 1;
		return rounds.stream()
				.filter(r -> r.questionIndex() == currentQuestionIndex)
				.mapToInt(InterviewRoundRecord::followUpIndex)
				.max()
				.orElse(0);
	}

	private long parseUserId(String userId) {
		try {
			return Long.parseLong(userId);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("userId 格式非法");
		}
	}

	private LocalDateTime parseDateTime(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return LocalDateTime.parse(value);
		} catch (Exception ex) {
			try {
				return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
			} catch (Exception ignored) {
				return null;
			}
		}
	}

	private record SessionRow(long id, String sessionKey, String userId, String status,
			int currentQuestionIndex, String configJson) {}

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

	private record SessionConfig(String ownerNickname, int maxFollowUpPerQuestion,
			Integer interviewerSpeakerId, Double interviewerSpeechSpeed, String stage, Integer durationMinutes) {

		private SessionConfig {
			ownerNickname = ownerNickname == null || ownerNickname.isBlank() ? "面试者" : ownerNickname;
			maxFollowUpPerQuestion = maxFollowUpPerQuestion <= 0 ? 2 : maxFollowUpPerQuestion;
			interviewerSpeechSpeed = interviewerSpeechSpeed == null ? 1.0 : interviewerSpeechSpeed;
			stage = stage == null || stage.isBlank() ? InterviewStage.OPENING.name() : stage;
			durationMinutes = durationMinutes == null || durationMinutes <= 0 ? 60 : durationMinutes;
		}
	}
}
