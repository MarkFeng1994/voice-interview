package com.interview.module.library.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.interview.module.library.entity.CategoryEntity;
import com.interview.module.library.entity.QuestionEntity;
import com.interview.module.library.mapper.CategoryMapper;
import com.interview.module.library.mapper.QuestionMapper;
import com.interview.module.library.service.LibraryCategory;

@Repository
@Profile("dev")
public class LibraryCategoryRepository {

	private final CategoryMapper categoryMapper;
	private final QuestionMapper questionMapper;

	public LibraryCategoryRepository(CategoryMapper categoryMapper, QuestionMapper questionMapper) {
		this.categoryMapper = categoryMapper;
		this.questionMapper = questionMapper;
	}

	public List<LibraryCategory> findAllByUserId(String userId) {
		return categoryMapper.selectList(
				new LambdaQueryWrapper<CategoryEntity>()
						.eq(CategoryEntity::getUserId, parseId(userId, "userId"))
						.orderByAsc(CategoryEntity::getParentId)
						.orderByAsc(CategoryEntity::getSortOrder)
						.orderByAsc(CategoryEntity::getId)
		).stream().map(this::toRecord).toList();
	}

	public Optional<LibraryCategory> findById(String userId, String categoryId) {
		CategoryEntity entity = categoryMapper.selectOne(
				new LambdaQueryWrapper<CategoryEntity>()
						.eq(CategoryEntity::getId, parseId(categoryId, "categoryId"))
						.eq(CategoryEntity::getUserId, parseId(userId, "userId"))
		);
		return Optional.ofNullable(entity).map(this::toRecord);
	}

	public LibraryCategory save(String userId, String name, String parentId, Integer sortOrder) {
		CategoryEntity entity = new CategoryEntity();
		entity.setUserId(parseId(userId, "userId"));
		entity.setName(name);
		entity.setParentId(parseParentId(parentId));
		entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
		categoryMapper.insert(entity);
		return toRecord(entity);
	}

	public LibraryCategory update(String userId, String categoryId, String name, String parentId, Integer sortOrder) {
		CategoryEntity entity = categoryMapper.selectOne(
				new LambdaQueryWrapper<CategoryEntity>()
						.eq(CategoryEntity::getId, parseId(categoryId, "categoryId"))
						.eq(CategoryEntity::getUserId, parseId(userId, "userId"))
		);
		if (entity == null) {
			throw new IllegalArgumentException("分类不存在");
		}
		entity.setName(name);
		entity.setParentId(parseParentId(parentId));
		entity.setSortOrder(sortOrder == null ? 0 : sortOrder);
		categoryMapper.updateById(entity);
		return toRecord(entity);
	}

	public void softDelete(String userId, String categoryId) {
		int updated = categoryMapper.delete(
				new LambdaQueryWrapper<CategoryEntity>()
						.eq(CategoryEntity::getId, parseId(categoryId, "categoryId"))
						.eq(CategoryEntity::getUserId, parseId(userId, "userId"))
		);
		if (updated == 0) {
			throw new IllegalArgumentException("分类不存在");
		}
	}

	public boolean hasChildren(String userId, String categoryId) {
		return categoryMapper.selectCount(
				new LambdaQueryWrapper<CategoryEntity>()
						.eq(CategoryEntity::getUserId, parseId(userId, "userId"))
						.eq(CategoryEntity::getParentId, parseId(categoryId, "categoryId"))
		) > 0;
	}

	public boolean hasQuestions(String userId, String categoryId) {
		return questionMapper.selectCount(
				new LambdaQueryWrapper<QuestionEntity>()
						.eq(QuestionEntity::getUserId, parseId(userId, "userId"))
						.eq(QuestionEntity::getCategoryId, parseId(categoryId, "categoryId"))
		) > 0;
	}

	private LibraryCategory toRecord(CategoryEntity e) {
		return new LibraryCategory(
				String.valueOf(e.getId()),
				String.valueOf(e.getUserId()),
				e.getName(),
				String.valueOf(e.getParentId()),
				e.getSortOrder()
		);
	}

	private long parseParentId(String parentId) {
		if (parentId == null || parentId.isBlank()) return 0L;
		return parseId(parentId, "parentId");
	}

	private long parseId(String value, String field) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(field + " 格式非法");
		}
	}
}
