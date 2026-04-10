package com.interview.module.interview.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.interview.module.interview.engine.InterviewEngine;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.model.InterviewSessionSummaryView;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.resume.ResumeInterviewPlan;
import com.interview.module.interview.resume.ResumeInterviewPlannerService;
import com.interview.module.interview.service.InterviewPresetCatalog.InterviewPresetView;
import com.interview.module.interview.service.InterviewPresetCatalog.PresetDefinition;

@Service
public class InterviewPracticeService {

	private final InterviewEngine interviewEngine;
	private final InterviewPresetCatalog interviewPresetCatalog;
	private final ResumeInterviewPlannerService resumePlannerService;
	private final InterviewFlowPolicy interviewFlowPolicy;
	private final ApplicationEventPublisher eventPublisher;

	public InterviewPracticeService(
			InterviewEngine interviewEngine,
			InterviewPresetCatalog interviewPresetCatalog,
			ResumeInterviewPlannerService resumePlannerService,
			InterviewFlowPolicy interviewFlowPolicy,
			ApplicationEventPublisher eventPublisher
	) {
		this.interviewEngine = interviewEngine;
		this.interviewPresetCatalog = interviewPresetCatalog;
		this.resumePlannerService = resumePlannerService;
		this.interviewFlowPolicy = interviewFlowPolicy;
		this.eventPublisher = eventPublisher;
	}

	public InterviewSessionView startSession(InterviewSessionOwner owner, String presetKey) {
		return startSession(owner, presetKey, null, null, null);
	}

	public InterviewSessionView startSession(
			InterviewSessionOwner owner,
			String presetKey,
			Integer durationMinutes,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
		PresetDefinition preset = interviewPresetCatalog.resolve(presetKey);
		InterviewFlowPolicy.DurationProfile profile = interviewFlowPolicy.resolve(durationMinutes);
		List<InterviewQuestionCard> composedQuestions = composeQuestions(
				List.of(),
				List.of(),
				preset.questions(),
				"OPENING"
		).stream().limit(profile.mainQuestionCount()).toList();
		InterviewSessionView view = interviewEngine.startSession(
				composedQuestions,
				profile.durationMinutes(),
				profile.maxFollowUpPerQuestion(),
				owner,
				interviewerSpeakerId,
				interviewerSpeechSpeed
		);
		publishSessionUpdated(owner.userId(), view);
		return view;
	}

	public List<InterviewPresetView> listPresets() {
		return interviewPresetCatalog.list();
	}

	public ResumeInterviewPlan previewResumePlan(String userId, String resumeFileId, String presetKey, Integer questionCount) {
		return resumePlannerService.plan(userId, resumeFileId, presetKey, questionCount);
	}

	public InterviewSessionView startResumeSession(
			InterviewSessionOwner owner,
			String resumeFileId,
			String presetKey,
			Integer questionCount,
			Integer durationMinutes,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
		ResumeInterviewPlan plan = resumePlannerService.plan(owner.userId(), resumeFileId, presetKey, questionCount);
		InterviewFlowPolicy.DurationProfile profile = interviewFlowPolicy.resolve(durationMinutes);
		List<InterviewQuestionCard> composedQuestions = composeQuestions(
				plan.questions().stream()
						.filter(question -> !"LIBRARY".equalsIgnoreCase(question.sourceType()) && !"PRESET".equalsIgnoreCase(question.sourceType()))
						.toList(),
				plan.questions().stream()
						.filter(question -> "LIBRARY".equalsIgnoreCase(question.sourceType()))
						.toList(),
				plan.questions().stream()
						.filter(question -> "PRESET".equalsIgnoreCase(question.sourceType()))
						.toList(),
				"OPENING"
		).stream().limit(profile.mainQuestionCount()).toList();
		InterviewSessionView view = interviewEngine.startSession(
				composedQuestions,
				profile.durationMinutes(),
				profile.maxFollowUpPerQuestion(),
				owner,
				interviewerSpeakerId,
				interviewerSpeechSpeed
		);
		publishSessionUpdated(owner.userId(), view);
		return view;
	}

	public InterviewSessionView getState(String sessionId, String requesterUserId) {
		return interviewEngine.getState(sessionId, requesterUserId);
	}

	public InterviewSessionView answer(
			String sessionId,
			String requesterUserId,
			String answerMode,
			String userText,
			String userAudioUrl
	) {
		InterviewSessionView view = interviewEngine.answer(sessionId, requesterUserId, answerMode, userText, userAudioUrl);
		publishSessionUpdated(requesterUserId, view);
		return view;
	}

	public InterviewSessionView skip(String sessionId, String requesterUserId) {
		InterviewSessionView view = interviewEngine.skip(sessionId, requesterUserId);
		publishSessionUpdated(requesterUserId, view);
		return view;
	}

	public InterviewSessionView end(String sessionId, String requesterUserId) {
		InterviewSessionView view = interviewEngine.end(sessionId, requesterUserId);
		publishSessionUpdated(requesterUserId, view);
		return view;
	}

	public List<InterviewSessionSummaryView> listSessions(String requesterUserId) {
		return interviewEngine.listSessions(requesterUserId);
	}

	public InterviewReportView getReport(String sessionId, String requesterUserId) {
		return interviewEngine.getReport(sessionId, requesterUserId);
	}

	private void publishSessionUpdated(String userId, InterviewSessionView view) {
		eventPublisher.publishEvent(new com.interview.module.interview.websocket.InterviewSessionUpdatedEvent(userId, view));
	}

	private List<InterviewQuestionCard> composeQuestions(
			List<InterviewQuestionCard> primaryQuestions,
			List<InterviewQuestionCard> libraryQuestions,
			List<InterviewQuestionCard> fallbackQuestions,
			String stage
	) {
		InterviewQuestionComposer composer = InterviewQuestionComposer.inMemory(
				primaryQuestions.stream()
						.map(this::toCandidateQuestion)
						.toList(),
				libraryQuestions.stream()
						.map(this::toCandidateQuestion)
						.toList(),
				fallbackQuestions.stream()
						.map(this::toCandidateQuestion)
						.toList()
		);
		return composer.compose(stage, List.of(), List.of()).stream()
				.map(question -> new InterviewQuestionCard(
						question.title(),
						question.prompt(),
						question.source(),
						question.sourceQuestionId(),
						question.sourceCategoryId(),
						question.difficulty()
				))
				.toList();
	}

	private InterviewQuestionComposer.CandidateQuestion toCandidateQuestion(InterviewQuestionCard question) {
		return new InterviewQuestionComposer.CandidateQuestion(
				question.title(),
				question.prompt(),
				question.sourceType(),
				question.sourceQuestionId(),
				question.sourceCategoryId(),
				question.difficulty()
		);
	}
}
