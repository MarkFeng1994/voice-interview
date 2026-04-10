package com.interview.module.library.controller;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;
import com.interview.module.library.service.LibraryCategory;
import com.interview.module.library.service.LibraryQuestion;
import com.interview.module.library.service.LibraryService;
import com.interview.module.user.service.CurrentUserResolver;
import com.interview.module.user.service.UserProfile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/library")
@Profile("dev")
public class LibraryController {

	private final LibraryService libraryService;
	private final CurrentUserResolver currentUserResolver;

	public LibraryController(
			LibraryService libraryService,
			CurrentUserResolver currentUserResolver
	) {
		this.libraryService = libraryService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/categories")
	public ApiResponse<List<LibraryCategory>> listCategories(HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.listCategories(profile.id()));
	}

	@PostMapping("/categories")
	public ApiResponse<LibraryCategory> createCategory(
			HttpServletRequest request,
			@RequestBody CategoryRequest body
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.createCategory(
				profile.id(),
				body.name(),
				body.parentId(),
				body.sortOrder()
		));
	}

	@PutMapping("/categories/{categoryId}")
	public ApiResponse<LibraryCategory> updateCategory(
			@PathVariable String categoryId,
			HttpServletRequest request,
			@RequestBody CategoryRequest body
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.updateCategory(
				profile.id(),
				categoryId,
				body.name(),
				body.parentId(),
				body.sortOrder()
		));
	}

	@DeleteMapping("/categories/{categoryId}")
	public ApiResponse<Void> deleteCategory(
			@PathVariable String categoryId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		libraryService.deleteCategory(profile.id(), categoryId);
		return ApiResponse.success(null);
	}

	@GetMapping("/questions")
	public ApiResponse<List<LibraryQuestion>> listQuestions(
			HttpServletRequest request,
			@RequestParam(required = false) String categoryId
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.listQuestions(profile.id(), categoryId));
	}

	@GetMapping("/imports")
	public ApiResponse<List<com.interview.module.library.service.ImportTaskRecord>> listImports(HttpServletRequest request) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.listRecentImportTasks(profile.id()));
	}

	@PostMapping("/imports/text")
	public ApiResponse<com.interview.module.library.service.LibraryService.ImportTextResult> importQuestionsFromText(
			HttpServletRequest request,
			@RequestBody ImportTextRequest body
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.importQuestionsFromText(
				profile.id(),
				body.categoryId(),
				body.rawText(),
				body.fileName()
		));
	}

	@PostMapping("/questions")
	public ApiResponse<LibraryQuestion> createQuestion(
			HttpServletRequest request,
			@RequestBody QuestionRequest body
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.createQuestion(
				profile.id(),
				body.categoryId(),
				body.title(),
				body.content(),
				body.answer(),
				body.difficulty(),
				body.source(),
				body.sourceUrl()
		));
	}

	@PutMapping("/questions/{questionId}")
	public ApiResponse<LibraryQuestion> updateQuestion(
			@PathVariable String questionId,
			HttpServletRequest request,
			@RequestBody QuestionRequest body
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		return ApiResponse.success(libraryService.updateQuestion(
				profile.id(),
				questionId,
				body.categoryId(),
				body.title(),
				body.content(),
				body.answer(),
				body.difficulty(),
				body.source(),
				body.sourceUrl()
		));
	}

	@DeleteMapping("/questions/{questionId}")
	public ApiResponse<Void> deleteQuestion(
			@PathVariable String questionId,
			HttpServletRequest request
	) {
		UserProfile profile = currentUserResolver.requireProfile(request);
		libraryService.deleteQuestion(profile.id(), questionId);
		return ApiResponse.success(null);
	}

	public record CategoryRequest(
			String name,
			String parentId,
			Integer sortOrder
	) {
	}

	public record QuestionRequest(
			String categoryId,
			String title,
			String content,
			String answer,
			Integer difficulty,
			String source,
			String sourceUrl
	) {
	}

	public record ImportTextRequest(
			String categoryId,
			String rawText,
			String fileName
	) {
	}
}
