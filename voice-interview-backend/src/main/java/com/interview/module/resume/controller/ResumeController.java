package com.interview.module.resume.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.interview.common.result.ApiResponse;
import com.interview.module.interview.resume.ResumeInterviewPlannerService;
import com.interview.module.interview.resume.ResumeInterviewPlannerService.ResumeInsights;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;
import com.interview.module.resume.service.ResumeProfileService;
import com.interview.module.resume.service.ResumeProfile;
import com.interview.module.user.service.CurrentUserResolver;
import com.interview.module.user.service.UserProfile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@Profile("dev")
@RequestMapping("/api/resumes")
public class ResumeController {

	private final ResumeProfileService resumeProfileService;
	private final ResumeInterviewPlannerService resumeInterviewPlannerService;
	private final LocalMediaStorageService mediaStorageService;
	private final CurrentUserResolver currentUserResolver;

	public ResumeController(
			ResumeProfileService resumeProfileService,
			ResumeInterviewPlannerService resumeInterviewPlannerService,
			LocalMediaStorageService mediaStorageService,
			CurrentUserResolver currentUserResolver
	) {
		this.resumeProfileService = resumeProfileService;
		this.resumeInterviewPlannerService = resumeInterviewPlannerService;
		this.mediaStorageService = mediaStorageService;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ResumeProfile> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		requirePdf(file);
		StoredMediaFile storedFile = mediaStorageService.store(file);
		ResumeProfile pendingProfile = resumeProfileService.createPendingProfile(
				profile.id(),
				storedFile.fileId(),
				storedFile.originalFileName(),
				storedFile.contentType(),
				storedFile.size()
		);
		return ApiResponse.success(parseAndPersist(profile.id(), pendingProfile));
	}

	@GetMapping("/{resumeId}")
	public ApiResponse<ResumeProfile> detail(@PathVariable String resumeId, HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(resumeProfileService.getDetail(profile.id(), resumeId));
	}

	@PostMapping("/{resumeId}/reparse")
	public ApiResponse<ResumeProfile> reparse(@PathVariable String resumeId, HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		ResumeProfile resumeProfile = resumeProfileService.getDetail(profile.id(), resumeId);
		return ApiResponse.success(parseAndPersist(profile.id(), resumeProfile));
	}

	private ResumeProfile parseAndPersist(String userId, ResumeProfile resumeProfile) {
		resumeProfileService.markParsing(userId, resumeProfile.resumeId());
		try {
			ResumeInsights insights = resumeInterviewPlannerService.analyzeResume(resumeProfile.mediaFileId());
			return resumeProfileService.markParsed(
					userId,
					resumeProfile.resumeId(),
					insights.resumeSummary(),
					insights.extractedKeywords(),
					insights.projectHighlights()
			);
		} catch (RuntimeException ex) {
			String parseError = ex.getMessage() == null || ex.getMessage().isBlank() ? "简历解析失败" : ex.getMessage();
			return resumeProfileService.markFailed(userId, resumeProfile.resumeId(), parseError);
		}
	}

	private void requirePdf(MultipartFile file) {
		String contentType = file.getContentType();
		String originalName = file.getOriginalFilename();
		boolean isPdf = (contentType != null && contentType.contains("pdf"))
				|| (originalName != null && originalName.toLowerCase().endsWith(".pdf"));
		if (!isPdf) {
			throw new IllegalArgumentException("只支持 PDF 格式的简历文件");
		}
	}
}
