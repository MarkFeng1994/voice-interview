package com.interview.module.interview.engine.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.engine.model.RealtimeMetrics;
import com.interview.module.interview.service.AnswerEvidence;
import com.interview.module.interview.service.FollowUpDecision;

public class InterviewSessionState {

	private final String sessionId;
	private final String ownerUserId;
	private final String ownerNickname;
	private final List<InterviewQuestionSnapshot> questions;
	private final int maxFollowUpPerQuestion;
	private final Integer interviewerSpeakerId;
	private final Double interviewerSpeechSpeed;
	private final List<InterviewRoundRecord> rounds = new ArrayList<>();
	private String status = "IN_PROGRESS";
	private String stage;
	private int durationMinutes;
	private int currentQuestionIndex = 0;
	private int followUpIndex = 0;
	private String interviewMode = "standard";
	private Long lastInterruptedAt;
	private int realtimeTurnCount;
	private RealtimeMetrics realtimeMetrics;

	public InterviewSessionState(
			String sessionId,
			String ownerUserId,
			String ownerNickname,
			List<InterviewQuestionSnapshot> questions,
			int maxFollowUpPerQuestion,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
		this(
				sessionId,
				ownerUserId,
				ownerNickname,
				questions,
				InterviewStage.OPENING.name(),
				60,
				maxFollowUpPerQuestion,
				interviewerSpeakerId,
				interviewerSpeechSpeed
		);
	}

	public InterviewSessionState(
			String sessionId,
			String ownerUserId,
			String ownerNickname,
			List<InterviewQuestionSnapshot> questions,
			String stage,
			int durationMinutes,
			int maxFollowUpPerQuestion,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
		this.sessionId = sessionId;
		this.ownerUserId = ownerUserId;
		this.ownerNickname = ownerNickname;
		this.questions = questions;
		this.stage = stage;
		this.durationMinutes = durationMinutes;
		this.maxFollowUpPerQuestion = maxFollowUpPerQuestion;
		this.interviewerSpeakerId = interviewerSpeakerId;
		this.interviewerSpeechSpeed = interviewerSpeechSpeed;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getOwnerUserId() {
		return ownerUserId;
	}

	public String getOwnerNickname() {
		return ownerNickname;
	}

	public List<InterviewQuestionSnapshot> getQuestions() {
		return questions;
	}

	public int getMaxFollowUpPerQuestion() {
		return maxFollowUpPerQuestion;
	}

	public Integer getInterviewerSpeakerId() {
		return interviewerSpeakerId;
	}

	public Double getInterviewerSpeechSpeed() {
		return interviewerSpeechSpeed;
	}

	public List<InterviewRoundRecord> getRounds() {
		return rounds;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getStage() {
		return stage;
	}

	public void setStage(String stage) {
		this.stage = stage;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public void setDurationMinutes(int durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}

	public void setCurrentQuestionIndex(int currentQuestionIndex) {
		this.currentQuestionIndex = currentQuestionIndex;
	}

	public int getFollowUpIndex() {
		return followUpIndex;
	}

	public void setFollowUpIndex(int followUpIndex) {
		this.followUpIndex = followUpIndex;
	}

	public String getInterviewMode() {
		return interviewMode;
	}

	public void setInterviewMode(String interviewMode) {
		this.interviewMode = interviewMode;
	}

	public Long getLastInterruptedAt() {
		return lastInterruptedAt;
	}

	public void setLastInterruptedAt(Long lastInterruptedAt) {
		this.lastInterruptedAt = lastInterruptedAt;
	}

	public int getRealtimeTurnCount() {
		return realtimeTurnCount;
	}

	public void setRealtimeTurnCount(int realtimeTurnCount) {
		this.realtimeTurnCount = realtimeTurnCount;
	}

	public RealtimeMetrics getRealtimeMetrics() {
		return realtimeMetrics;
	}

	public void setRealtimeMetrics(RealtimeMetrics realtimeMetrics) {
		this.realtimeMetrics = realtimeMetrics;
	}

	public InterviewQuestionSnapshot getCurrentQuestion() {
		if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
			return null;
		}
		return questions.get(currentQuestionIndex);
	}

	public void appendRealtimeUserAnswer(String text) {
		String now = java.time.Instant.now().toString();
		InterviewRoundRecord round = new InterviewRoundRecord(
				UUID.randomUUID().toString(),
				currentQuestionIndex,
				followUpIndex,
				"USER",
				null, null, 0, null,
				text, null, "REALTIME",
				now, now,
				null, null, null, null
		);
		rounds.add(round);
		realtimeTurnCount++;
	}

	public void appendRealtimeAiReply(String text, AnswerEvidence evidence, FollowUpDecision decision) {
		String now = java.time.Instant.now().toString();
		InterviewRoundRecord round = new InterviewRoundRecord(
				UUID.randomUUID().toString(),
				currentQuestionIndex,
				followUpIndex,
				"ASSISTANT",
				text, null, 0, null,
				null, null, "REALTIME",
				now, null,
				evidence != null ? evidence.summaryReason() : null,
				decision != null ? decision.action().name() : null,
				decision != null ? decision.reasonText() : null,
				evidence != null ? evidence.missingPoints() : null
		);
		rounds.add(round);
	}

	public InterviewSessionSnapshot toSnapshot() {
		return new InterviewSessionSnapshot(
				sessionId,
				ownerUserId,
				ownerNickname,
				List.copyOf(questions),
				stage,
				durationMinutes,
				maxFollowUpPerQuestion,
				interviewerSpeakerId,
				interviewerSpeechSpeed,
				status,
				currentQuestionIndex,
				followUpIndex,
				List.copyOf(rounds),
				interviewMode,
				realtimeTurnCount,
				realtimeMetrics
		);
	}

	public static InterviewSessionState fromSnapshot(InterviewSessionSnapshot snapshot) {
		InterviewSessionState state = new InterviewSessionState(
				snapshot.sessionId(),
				snapshot.ownerUserId(),
				snapshot.ownerNickname(),
				snapshot.questions(),
				snapshot.stage(),
				snapshot.durationMinutes(),
				snapshot.maxFollowUpPerQuestion(),
				snapshot.interviewerSpeakerId(),
				snapshot.interviewerSpeechSpeed()
		);
		state.setStatus(snapshot.status());
		state.setCurrentQuestionIndex(snapshot.currentQuestionIndex());
		state.setFollowUpIndex(snapshot.followUpIndex());
		state.getRounds().addAll(snapshot.rounds());
		state.setInterviewMode(snapshot.interviewMode());
		state.setRealtimeTurnCount(snapshot.realtimeTurnCount());
		state.setRealtimeMetrics(snapshot.realtimeMetrics());
		return state;
	}

	public InterviewSessionView toView() {
		InterviewQuestionSnapshot currentQ = getCurrentQuestion();
		return new InterviewSessionView(
				sessionId,
				status,
				stage,
				durationMinutes,
				currentQuestionIndex + 1,
				questions.size(),
				followUpIndex,
				maxFollowUpPerQuestion,
				currentQ != null ? currentQ.titleSnapshot() : null,
				currentQ != null ? currentQ.promptSnapshot() : null,
				List.copyOf(questions),
				List.copyOf(rounds),
				List.of()
		);
	}
}
