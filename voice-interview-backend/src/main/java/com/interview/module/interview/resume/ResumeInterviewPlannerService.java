package com.interview.module.interview.resume;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.interview.common.exception.AppException;
import com.interview.module.ai.service.AiService;
import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.resume.ResumeQuestionMatcher.MatchResult;
import com.interview.module.interview.service.InterviewPresetCatalog;
import com.interview.module.interview.service.InterviewPresetCatalog.PresetDefinition;
import com.interview.module.library.service.LibraryCategory;
import com.interview.module.library.service.LibraryQuestion;
import com.interview.module.library.service.LibraryService;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;

@Service
public class ResumeInterviewPlannerService {

	private final ResumeTextExtractor resumeTextExtractor;
	private final ResumeQuestionMatcher resumeQuestionMatcher;
	private final AiService aiService;
	private final LocalMediaStorageService mediaStorageService;
	private final InterviewPresetCatalog interviewPresetCatalog;
	private final ObjectProvider<LibraryService> libraryServiceProvider;
	private final int defaultQuestionCount;
	private final int minQuestionCount;
	private final int maxQuestionCount;

	public ResumeInterviewPlannerService(
			ResumeTextExtractor resumeTextExtractor,
			ResumeQuestionMatcher resumeQuestionMatcher,
			AiService aiService,
			LocalMediaStorageService mediaStorageService,
			InterviewPresetCatalog interviewPresetCatalog,
			ObjectProvider<LibraryService> libraryServiceProvider,
			@Value("${app.interview.resume.default-question-count:5}") int defaultQuestionCount,
			@Value("${app.interview.resume.min-question-count:3}") int minQuestionCount,
			@Value("${app.interview.resume.max-question-count:8}") int maxQuestionCount
	) {
		this.resumeTextExtractor = resumeTextExtractor;
		this.resumeQuestionMatcher = resumeQuestionMatcher;
		this.aiService = aiService;
		this.mediaStorageService = mediaStorageService;
		this.interviewPresetCatalog = interviewPresetCatalog;
		this.libraryServiceProvider = libraryServiceProvider;
		this.defaultQuestionCount = defaultQuestionCount;
		this.minQuestionCount = minQuestionCount;
		this.maxQuestionCount = maxQuestionCount;
	}

	public ResumeInterviewPlan plan(String userId, String resumeFileId, String presetKey, Integer requestedQuestionCount) {
		int target = resolveQuestionCount(requestedQuestionCount);
		if (!StringUtils.hasText(resumeFileId)) {
			return buildPresetOnlyPlan(presetKey);
		}

		ResumeInsights resumeInsights = analyzeResume(resumeFileId);
		List<String> keywords = resumeInsights.extractedKeywords();

		List<LibraryCategory> categories = List.of();
		List<LibraryQuestion> libraryQuestions = List.of();
		LibraryService libraryService = libraryServiceProvider.getIfAvailable();
		if (libraryService != null && StringUtils.hasText(userId)) {
			categories = libraryService.listCategories(userId);
			libraryQuestions = libraryService.listQuestions(userId, null);
		}

		MatchResult matchResult = resumeQuestionMatcher.match(keywords, categories, libraryQuestions, target);

		List<InterviewQuestionCard> finalQuestions = new ArrayList<>();
		Set<String> dedupeKeys = new LinkedHashSet<>();

		for (LibraryQuestion q : matchResult.matchedQuestions()) {
			addQuestion(finalQuestions, dedupeKeys,
					new InterviewQuestionCard(q.title(),
							StringUtils.hasText(q.content()) ? q.content() : q.title(),
							"LIBRARY", q.id(), q.categoryId(), q.difficulty()),
					target);
		}

		List<GeneratedResumeQuestion> generatedQuestions = safeGenerateQuestions(
				new ResumeQuestionGenerationCommand(
						resumeInsights.resumeSummary(), keywords,
						finalQuestions.stream().map(InterviewQuestionCard::title).toList(),
						matchResult.missingKeywords(),
						Math.max(0, target - finalQuestions.size())));

		List<GeneratedResumeQuestion> usedGenerated = new ArrayList<>();
		for (GeneratedResumeQuestion gq : generatedQuestions) {
			boolean added = addQuestion(finalQuestions, dedupeKeys,
					new InterviewQuestionCard(gq.title(), gq.prompt(), "AI_RESUME", null, null, gq.difficulty()),
					target);
			if (added) usedGenerated.add(gq);
		}

		boolean usedPresetFallback = false;
		if (finalQuestions.size() < target) {
			usedPresetFallback = true;
			PresetDefinition preset = interviewPresetCatalog.resolve(presetKey);
			for (InterviewQuestionCard pq : preset.questions()) {
				addQuestion(finalQuestions, dedupeKeys, pq, target);
			}
		}

		if (finalQuestions.isEmpty()) {
			throw AppException.conflict("RESUME_PLAN_EMPTY", "没有生成可用的面试题，请检查简历内容或题库配置");
		}

		return new ResumeInterviewPlan(
				resumeInsights.mediaFileId(), resumeInsights.resumeSummary(),
				matchResult.normalizedKeywords(), matchResult.matchedCategoryNames(),
				matchResult.matchedQuestions().stream().map(LibraryQuestion::title).toList(),
				matchResult.missingKeywords(), usedGenerated, finalQuestions, usedPresetFallback);
	}

	public ResumeInsights analyzeResume(String resumeFileId) {
		StoredMediaFile resumeFile = mediaStorageService.load(resumeFileId.trim());
		requirePdf(resumeFile);

		ResumeTextExtractor.ResumeText extracted = resumeTextExtractor.extract(resumeFile);
		if (!StringUtils.hasText(extracted.plainText())) {
			throw AppException.badRequest("RESUME_TEXT_EMPTY", "简历内容为空，暂时无法生成个性化面试题");
		}

		ResumeKeywordExtractionResult extraction = safeExtractKeywords(extracted.plainText());
		List<String> keywords = extraction.hasKeywords()
				? extraction.keywords()
				: resumeQuestionMatcher.extractKeywordsFromTextFallback(extracted.plainText());
		String summary = StringUtils.hasText(extraction.summary())
				? extraction.summary()
				: fallbackSummary(extracted.plainText());
		return new ResumeInsights(
				resumeFile.fileId(),
				summary,
				keywords,
				extraction.experienceHighlights(),
				extracted.plainText()
		);
	}

	private ResumeInterviewPlan buildPresetOnlyPlan(String presetKey) {
		PresetDefinition preset = interviewPresetCatalog.resolve(presetKey);
		return new ResumeInterviewPlan(null, preset.summary(), preset.tags(),
				List.of(), List.of(), List.of(), List.of(), preset.questions(), true);
	}

	private ResumeKeywordExtractionResult safeExtractKeywords(String resumeText) {
		try {
			return aiService.extractResumeKeywords(resumeText);
		} catch (RuntimeException ex) {
			List<String> fallback = resumeQuestionMatcher.extractKeywordsFromTextFallback(resumeText);
			String summary = resumeText.length() <= 120 ? resumeText : resumeText.substring(0, 120).trim();
			return new ResumeKeywordExtractionResult(summary, fallback, List.of());
		}
	}

	private List<GeneratedResumeQuestion> safeGenerateQuestions(ResumeQuestionGenerationCommand command) {
		if (command.questionCount() <= 0) return List.of();
		try {
			return aiService.generateResumeQuestions(command);
		} catch (RuntimeException ex) {
			return List.of();
		}
	}

	private boolean addQuestion(List<InterviewQuestionCard> list, Set<String> dedupeKeys,
			InterviewQuestionCard q, int limit) {
		if (list.size() >= limit) return false;
		String key = normalize(q.title()) + "::" + normalize(q.prompt());
		if (!dedupeKeys.add(key)) return false;
		list.add(q);
		return true;
	}

	private int resolveQuestionCount(Integer requested) {
		int min = Math.max(1, minQuestionCount);
		int max = Math.max(min, maxQuestionCount);
		int def = Math.max(min, Math.min(max, defaultQuestionCount));
		int t = requested == null ? def : requested;
		return Math.max(min, Math.min(max, t));
	}

	private void requirePdf(StoredMediaFile f) {
		String name = f.storedFileName() == null ? "" : f.storedFileName().toLowerCase();
		String ct = f.contentType() == null ? "" : f.contentType().toLowerCase();
		if (!name.endsWith(".pdf") && !ct.contains("pdf")) {
			throw AppException.badRequest("INVALID_RESUME_FILE", "简历文件必须是 PDF 格式");
		}
	}

	private String normalize(String v) {
		return !StringUtils.hasText(v) ? "" : v.trim().toLowerCase().replaceAll("[\\s_\\-]+", "");
	}

	private String fallbackSummary(String plainText) {
		if (!StringUtils.hasText(plainText)) {
			return "";
		}
		String normalized = plainText.trim();
		return normalized.length() <= 120 ? normalized : normalized.substring(0, 120).trim();
	}

	public record ResumeInsights(
			String mediaFileId,
			String resumeSummary,
			List<String> extractedKeywords,
			List<String> projectHighlights,
			String plainText
	) {
	}
}
