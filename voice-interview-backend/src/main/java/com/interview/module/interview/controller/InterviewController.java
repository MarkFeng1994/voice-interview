package com.interview.module.interview.controller;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.ai.service.AiReply;
import com.interview.module.ai.service.AiService;
import com.interview.module.asr.service.AsrService;
import com.interview.module.asr.service.AsrTranscription;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.model.InterviewSessionSummaryView;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.resume.ResumeInterviewPlan;
import com.interview.module.interview.service.InterviewPracticeService;
import com.interview.module.interview.service.InterviewPresetCatalog.InterviewPresetView;
import com.interview.module.interview.websocket.InterviewWsTicketService;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.tts.service.TtsAudioResult;
import com.interview.module.tts.service.TtsService;
import com.interview.module.user.service.CurrentUserResolver;
import com.interview.module.user.service.UserProfile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

	private final AiService aiService;
	private final TtsService ttsService;
	private final LocalMediaStorageService mediaStorageService;
	private final AsrService asrService;
	private final InterviewPracticeService interviewPracticeService;
	private final CurrentUserResolver currentUserResolver;
	private final InterviewWsTicketService interviewWsTicketService;

	public InterviewController(
			AiService aiService,
			TtsService ttsService,
			LocalMediaStorageService mediaStorageService,
			AsrService asrService,
			InterviewPracticeService interviewPracticeService,
			CurrentUserResolver currentUserResolver,
			InterviewWsTicketService interviewWsTicketService
	) {
		this.aiService = aiService;
		this.ttsService = ttsService;
		this.mediaStorageService = mediaStorageService;
		this.asrService = asrService;
		this.interviewPracticeService = interviewPracticeService;
		this.currentUserResolver = currentUserResolver;
		this.interviewWsTicketService = interviewWsTicketService;
	}

	@PostMapping
	public ApiResponse<InterviewSessionView> startSession(
			HttpServletRequest request,
			@RequestBody(required = false) StartInterviewRequest startInterviewRequest
	) {
		InterviewSessionOwner owner = currentUserResolver.requireOwner(request);
		if (startInterviewRequest != null && startInterviewRequest.resumeFileId() != null
				&& !startInterviewRequest.resumeFileId().isBlank()) {
			return ApiResponse.success(interviewPracticeService.startResumeSession(
					owner,
					startInterviewRequest.resumeFileId(),
					startInterviewRequest.presetKey(),
					startInterviewRequest.questionCount(),
					startInterviewRequest.durationMinutes(),
					startInterviewRequest.interviewerSpeakerId(),
					startInterviewRequest.interviewerSpeechSpeed()
			));
		}
		return ApiResponse.success(interviewPracticeService.startSession(
				owner,
				startInterviewRequest == null ? null : startInterviewRequest.presetKey(),
				startInterviewRequest == null ? null : startInterviewRequest.durationMinutes(),
				startInterviewRequest == null ? null : startInterviewRequest.interviewerSpeakerId(),
				startInterviewRequest == null ? null : startInterviewRequest.interviewerSpeechSpeed()
		));
	}

	@GetMapping("/presets")
	public ApiResponse<java.util.List<InterviewPresetView>> listPresets() {
		return ApiResponse.success(interviewPracticeService.listPresets());
	}

	@GetMapping
	public ApiResponse<java.util.List<InterviewSessionSummaryView>> listSessions(HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(interviewPracticeService.listSessions(profile.id()));
	}

	@GetMapping("/{sessionId}/state")
	public ApiResponse<InterviewSessionView> getSessionState(
			@PathVariable String sessionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(interviewPracticeService.getState(sessionId, profile.id()));
	}

	@GetMapping("/{sessionId}/report")
	public ApiResponse<InterviewReportView> getSessionReport(
			@PathVariable String sessionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(interviewPracticeService.getReport(sessionId, profile.id()));
	}

	@PostMapping("/{sessionId}/ws-ticket")
	public ApiResponse<WsTicketPayload> issueWsTicket(
			@PathVariable String sessionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		interviewPracticeService.getState(sessionId, profile.id());
		return ApiResponse.success(new WsTicketPayload(
				interviewWsTicketService.issue(profile.id(), sessionId),
				sessionId
		));
	}

	@PostMapping("/{sessionId}/answer")
	public ApiResponse<InterviewSessionView> answer(
			@PathVariable String sessionId,
			@RequestBody InterviewAnswerRequest request,
			HttpServletRequest httpServletRequest
	) {
		UserProfile profile = currentUserResolver.requireProfile(httpServletRequest);
		String userText;
		String answerMode;
		String userAudioUrl = null;
		if (StringUtils.hasText(request.fileId())) {
			StoredMediaFile mediaFile = mediaStorageService.load(request.fileId());
			userAudioUrl = buildMediaUrl(mediaFile.fileId());
			if (StringUtils.hasText(request.textAnswer())) {
				userText = request.textAnswer().trim();
			} else {
				AsrTranscription transcription = asrService.transcribe(mediaFile);
				userText = transcription.transcript();
			}
			answerMode = "VOICE";
		} else {
			userText = request.textAnswer();
			answerMode = "TEXT";
		}
		return ApiResponse.success(interviewPracticeService.answer(sessionId, profile.id(), answerMode, userText, userAudioUrl));
	}

	@PostMapping("/{sessionId}/skip")
	public ApiResponse<InterviewSessionView> skip(
			@PathVariable String sessionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(interviewPracticeService.skip(sessionId, profile.id()));
	}

	@PostMapping("/{sessionId}/end")
	public ApiResponse<InterviewSessionView> end(
			@PathVariable String sessionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(interviewPracticeService.end(sessionId, profile.id()));
	}

	@PostMapping("/reply-preview")
	public ApiResponse<InterviewReplyPreviewPayload> replyPreview(@RequestBody InterviewReplyPreviewRequest request) {
		AiReply aiReply = aiService.generateInterviewReply(request.inputText());
		TtsAudioResult ttsAudio = ttsService.synthesize(aiReply.spokenText());
		return ApiResponse.success(new InterviewReplyPreviewPayload(
				request.inputText(),
				aiReply.spokenText(),
				aiReply.decisionSuggestion(),
				aiReply.scoreSuggestion(),
				ttsAudio.fileId(),
				ttsAudio.audioUrl(),
				ttsAudio.durationMs()
		));
	}

	@PostMapping("/resume-preview")
	public ApiResponse<ResumeInterviewPlan> resumePreview(
			@RequestBody ResumePreviewRequest request,
			HttpServletRequest httpServletRequest
	) {
		UserProfile profile = currentUserResolver.requireProfile(httpServletRequest);
		return ApiResponse.success(interviewPracticeService.previewResumePlan(
				profile.id(), request.resumeFileId(), request.presetKey(), request.questionCount()));
	}

	public record InterviewAnswerRequest(
			String fileId,
			String textAnswer
	) {
	}

	public record InterviewReplyPreviewRequest(
			String inputText
	) {
	}

	public record InterviewReplyPreviewPayload(
			String inputText,
			String spokenText,
			String decisionSuggestion,
			Integer scoreSuggestion,
			String audioFileId,
			String audioUrl,
			long audioDurationMs
	) {
	}

	public record StartInterviewRequest(
			String presetKey,
			String resumeFileId,
			Integer questionCount,
			Integer durationMinutes,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	) {
	}

	public record ResumePreviewRequest(
			String resumeFileId,
			String presetKey,
			Integer questionCount
	) {
	}

	public record WsTicketPayload(
			String ticket,
			String sessionId
	) {
	}

	private String buildMediaUrl(String fileId) {
		return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/media/")
				.path(fileId)
				.toUriString();
	}
}
