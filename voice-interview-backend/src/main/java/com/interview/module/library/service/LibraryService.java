package com.interview.module.library.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.interview.common.exception.AppException;
import com.interview.module.library.repository.LibraryCategoryRepository;
import com.interview.module.library.repository.ImportTaskRepository;
import com.interview.module.library.repository.LibraryQuestionRepository;

@Service
@Profile("dev")
public class LibraryService {

	private final LibraryCategoryRepository categoryRepository;
	private final LibraryQuestionRepository questionRepository;
	private final ImportTaskRepository importTaskRepository;

	public LibraryService(
			LibraryCategoryRepository categoryRepository,
			LibraryQuestionRepository questionRepository,
			ImportTaskRepository importTaskRepository
	) {
		this.categoryRepository = categoryRepository;
		this.questionRepository = questionRepository;
		this.importTaskRepository = importTaskRepository;
	}

	public List<LibraryCategory> listCategories(String userId) {
		return categoryRepository.findAllByUserId(userId);
	}

	public LibraryCategory createCategory(String userId, String name, String parentId, Integer sortOrder) {
		return categoryRepository.save(
				userId,
				normalizeRequired(name, "name"),
				parentId,
				sortOrder
		);
	}

	public LibraryCategory updateCategory(String userId, String categoryId, String name, String parentId, Integer sortOrder) {
		if (categoryId != null && categoryId.equals(parentId)) {
			throw new IllegalArgumentException("分类不能把自己设为父级");
		}
		return categoryRepository.update(
				userId,
				categoryId,
				normalizeRequired(name, "name"),
				parentId,
				sortOrder
		);
	}

	public void deleteCategory(String userId, String categoryId) {
		if (categoryRepository.hasChildren(userId, categoryId)) {
			throw new IllegalStateException("请先删除或迁移子分类");
		}
		if (categoryRepository.hasQuestions(userId, categoryId)) {
			throw new IllegalStateException("请先删除或迁移分类下的题目");
		}
		categoryRepository.softDelete(userId, categoryId);
	}

	public List<LibraryQuestion> listQuestions(String userId, String categoryId) {
		return questionRepository.findAllByUserId(userId, categoryId);
	}

	public List<LibraryQuestion> matchQuestionsByKeywords(String userId, List<String> keywords, int limit) {
		List<String> normalizedKeywords = keywords == null ? List.of() : keywords.stream()
				.filter(StringUtils::hasText)
				.map(String::trim)
				.toList();
		if (normalizedKeywords.isEmpty() || limit <= 0) {
			return List.of();
		}
		return listQuestions(userId, null).stream()
				.filter(question -> normalizedKeywords.stream().anyMatch(keyword -> containsKeyword(question, keyword)))
				.limit(limit)
				.toList();
	}

	public LibraryQuestion createQuestion(
			String userId,
			String categoryId,
			String title,
			String content,
			String answer,
			Integer difficulty,
			String source,
			String sourceUrl
	) {
		requireCategoryExists(userId, categoryId);
		return questionRepository.save(
				userId,
				categoryId,
				normalizeRequired(title, "title"),
				normalizeRequired(content, "content"),
				normalizeOptional(answer),
				difficulty,
				normalizeOptional(source),
				normalizeOptional(sourceUrl)
		);
	}

	public LibraryQuestion updateQuestion(
			String userId,
			String questionId,
			String categoryId,
			String title,
			String content,
			String answer,
			Integer difficulty,
			String source,
			String sourceUrl
	) {
		requireCategoryExists(userId, categoryId);
		return questionRepository.update(
				userId,
				questionId,
				categoryId,
				normalizeRequired(title, "title"),
				normalizeRequired(content, "content"),
				normalizeOptional(answer),
				difficulty,
				normalizeOptional(source),
				normalizeOptional(sourceUrl)
		);
	}

	public void deleteQuestion(String userId, String questionId) {
		questionRepository.softDelete(userId, questionId);
	}

	public ImportTextResult importQuestionsFromText(String userId, String categoryId, String rawText, String fileName) {
		requireCategoryExists(userId, categoryId);
		String normalized = normalizeRequired(rawText, "rawText");
		ImportTaskRecord task = importTaskRepository.create(userId, "TEXT", categoryId, fileName, null);
		importTaskRepository.markRunning(task.id());

		List<ParsedQuestionBlock> blocks = parseQuestionBlocks(normalized);
		int successCount = 0;
		try {
			for (ParsedQuestionBlock block : blocks) {
				questionRepository.save(
						userId,
						categoryId,
						block.title(),
						block.content(),
						null,
						1,
						"IMPORT",
						null
				);
				successCount++;
			}
			importTaskRepository.markSuccess(task.id(), blocks.size(), successCount);
			return new ImportTextResult(task.id(), blocks.size(), successCount, "导入完成");
		} catch (Exception ex) {
			importTaskRepository.markFailed(task.id(), blocks.size(), successCount, ex.getMessage());
			throw AppException.conflict("IMPORT_FAILED", "题库导入失败：" + ex.getMessage());
		}
	}

	public List<ImportTaskRecord> listRecentImportTasks(String userId) {
		return importTaskRepository.findRecentByUserId(userId, 20);
	}

	private void requireCategoryExists(String userId, String categoryId) {
		if (!StringUtils.hasText(categoryId)) {
			throw new IllegalArgumentException("categoryId 不能为空");
		}
		categoryRepository.findById(userId, categoryId)
				.orElseThrow(() -> new IllegalArgumentException("分类不存在"));
	}

	private String normalizeRequired(String value, String field) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(field + " 不能为空");
		}
		return value.trim();
	}

	private String normalizeOptional(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private List<ParsedQuestionBlock> parseQuestionBlocks(String rawText) {
		String normalized = rawText.replace("\r\n", "\n").trim();
		String[] blocks = normalized.split("\\n\\s*\\n+|\\n---+\\n");
		List<ParsedQuestionBlock> parsed = java.util.Arrays.stream(blocks)
				.map(String::trim)
				.filter(block -> !block.isBlank())
				.filter(block -> !block.replaceAll("\\s", "").matches("-+"))
				.map(this::parseQuestionBlock)
				.collect(Collectors.toList());
		if (parsed.isEmpty()) {
			throw AppException.badRequest("IMPORT_EMPTY", "没有解析到可导入的题目");
		}
		return parsed;
	}

	private ParsedQuestionBlock parseQuestionBlock(String block) {
		String[] lines = block.split("\\n");
		String title = lines[0].trim();
		if (!StringUtils.hasText(title)) {
			throw AppException.badRequest("IMPORT_INVALID_BLOCK", "导入块缺少标题");
		}
		String content;
		if (lines.length == 1) {
			content = title;
		} else {
			content = java.util.Arrays.stream(lines)
					.skip(1)
					.collect(Collectors.joining("\n"))
					.trim();
		}
		if (!StringUtils.hasText(content)) {
			content = title;
		}
		return new ParsedQuestionBlock(title, content);
	}

	private boolean containsKeyword(LibraryQuestion question, String keyword) {
		String normalizedKeyword = keyword.toLowerCase();
		return containsText(question.title(), normalizedKeyword) || containsText(question.content(), normalizedKeyword);
	}

	private boolean containsText(String text, String keyword) {
		return text != null && text.toLowerCase().contains(keyword);
	}

	public record ImportTextResult(
			String taskId,
			int totalCount,
			int successCount,
			String message
	) {
	}

	private record ParsedQuestionBlock(
			String title,
			String content
	) {
	}
}
