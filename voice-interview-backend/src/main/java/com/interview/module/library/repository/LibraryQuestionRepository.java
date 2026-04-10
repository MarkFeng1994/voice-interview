package com.interview.module.library.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.module.library.entity.QuestionEntity;
import com.interview.module.library.mapper.QuestionMapper;
import com.interview.module.library.service.LibraryQuestion;

@Repository
@Profile("dev")
public class LibraryQuestionRepository {

	private final QuestionMapper questionMapper;

	public LibraryQuestionRepository(QuestionMapper questionMapper) {
		this.questionMapper = questionMapper;
	}

	public List<LibraryQuestion> findAllByUserId(String userId, String categoryId) {
		LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<QuestionEntity>()
				.eq(QuestionEntity::getUserId, parseId(userId, "userId"));
		if (categoryId != null && !categoryId.isBlank()) {
			wrapper.eq(QuestionEntity::getCategoryId, parseId(categoryId, "categoryId"));
		}
		wrapper.orderByDesc(QuestionEntity::getId);
		return questionMapper.selectList(wrapper).stream().map(this::toRecord).toList();
	}

	public Optional<LibraryQuestion> findById(String userId, String questionId) {
		QuestionEntity entity = questionMapper.selectOne(
				new LambdaQueryWrapper<QuestionEntity>()
						.eq(QuestionEntity::getId, parseId(questionId, "questionId"))
						.eq(QuestionEntity::getUserId, parseId(userId, "userId"))
		);
		return Optional.ofNullable(entity).map(this::toRecord);
	}

	public LibraryQuestion save(String userId, String categoryId, String title, String content,
			String answer, Integer difficulty, String source, String sourceUrl) {
		QuestionEntity entity = new QuestionEntity();
		entity.setUserId(parseId(userId, "userId"));
		entity.setCategoryId(parseId(categoryId, "categoryId"));
		entity.setTitle(title);
		entity.setContent(content);
		entity.setAnswer(answer);
		entity.setDifficulty(difficulty == null ? 1 : difficulty);
		entity.setSource(source == null || source.isBlank() ? "MANUAL" : source);
		entity.setSourceUrl(sourceUrl);
		questionMapper.insert(entity);
		return toRecord(entity);
	}

	public LibraryQuestion update(String userId, String questionId, String categoryId, String title,
			String content, String answer, Integer difficulty, String source, String sourceUrl) {
		QuestionEntity entity = questionMapper.selectOne(
				new LambdaQueryWrapper<QuestionEntity>()
						.eq(QuestionEntity::getId, parseId(questionId, "questionId"))
						.eq(QuestionEntity::getUserId, parseId(userId, "userId"))
		);
		if (entity == null) {
			throw new IllegalArgumentException("题目不存在");
		}
		entity.setCategoryId(parseId(categoryId, "categoryId"));
		entity.setTitle(title);
		entity.setContent(content);
		entity.setAnswer(answer);
		entity.setDifficulty(difficulty == null ? 1 : difficulty);
		entity.setSource(source == null || source.isBlank() ? "MANUAL" : source);
		entity.setSourceUrl(sourceUrl);
		questionMapper.updateById(entity);
		return toRecord(entity);
	}

	public void softDelete(String userId, String questionId) {
		int updated = questionMapper.delete(
				new LambdaQueryWrapper<QuestionEntity>()
						.eq(QuestionEntity::getId, parseId(questionId, "questionId"))
						.eq(QuestionEntity::getUserId, parseId(userId, "userId"))
		);
		if (updated == 0) {
			throw new IllegalArgumentException("题目不存在");
		}
	}

	private LibraryQuestion toRecord(QuestionEntity e) {
		return new LibraryQuestion(
				String.valueOf(e.getId()),
				String.valueOf(e.getUserId()),
				String.valueOf(e.getCategoryId()),
				e.getTitle(),
				e.getContent(),
				e.getAnswer(),
				e.getDifficulty(),
				e.getSource(),
				e.getSourceUrl()
		);
	}

	private long parseId(String value, String field) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(field + " 格式非法");
		}
	}
}
