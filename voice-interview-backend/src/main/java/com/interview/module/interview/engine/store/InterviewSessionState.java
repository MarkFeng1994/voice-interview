package com.interview.module.interview.engine.store;

import java.util.ArrayList;
import java.util.List;

import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

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
				List.copyOf(rounds)
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
		return state;
	}
}
