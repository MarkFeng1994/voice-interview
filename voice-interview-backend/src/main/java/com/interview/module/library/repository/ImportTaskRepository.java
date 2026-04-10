package com.interview.module.library.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.module.library.entity.ImportTaskEntity;
import com.interview.module.library.mapper.ImportTaskMapper;
import com.interview.module.library.service.ImportTaskRecord;

@Repository
@Profile("dev")
public class ImportTaskRepository {

	private final ImportTaskMapper importTaskMapper;

	public ImportTaskRepository(ImportTaskMapper importTaskMapper) {
		this.importTaskMapper = importTaskMapper;
	}

	public ImportTaskRecord create(String userId, String type, String categoryId, String fileName, String sourceUrl) {
		ImportTaskEntity entity = new ImportTaskEntity();
		entity.setUserId(parseId(userId, "userId"));
		entity.setType(type);
		entity.setCategoryId(parseId(categoryId, "categoryId"));
		entity.setFileName(fileName);
		entity.setSourceUrl(sourceUrl);
		entity.setStatus("PENDING");
		entity.setTotalCount(0);
		entity.setSuccessCount(0);
		importTaskMapper.insert(entity);
		return toRecord(entity);
	}

	public List<ImportTaskRecord> findByUserId(String userId) {
		return importTaskMapper.selectList(
				new LambdaQueryWrapper<ImportTaskEntity>()
						.eq(ImportTaskEntity::getUserId, parseId(userId, "userId"))
						.orderByDesc(ImportTaskEntity::getId)
		).stream().map(this::toRecord).toList();
	}

	public void markRunning(String taskId) {
		updateProgress(taskId, "RUNNING", 0, 0, null);
	}

	public void markSuccess(String taskId, int totalCount, int successCount) {
		updateProgress(taskId, "SUCCESS", totalCount, successCount, null);
	}

	public void markFailed(String taskId, int totalCount, int successCount, String errorMsg) {
		updateProgress(taskId, "FAILED", totalCount, successCount, errorMsg);
	}

	public List<ImportTaskRecord> findRecentByUserId(String userId, int limit) {
		return importTaskMapper.selectList(
				new LambdaQueryWrapper<ImportTaskEntity>()
						.eq(ImportTaskEntity::getUserId, parseId(userId, "userId"))
						.orderByDesc(ImportTaskEntity::getId)
						.last("LIMIT " + limit)
		).stream().map(this::toRecord).toList();
	}

	public void updateProgress(String taskId, String status, int totalCount, int successCount, String errorMsg) {
		ImportTaskEntity entity = importTaskMapper.selectById(parseId(taskId, "taskId"));
		if (entity == null) {
			throw new IllegalArgumentException("导入任务不存在");
		}
		entity.setStatus(status);
		entity.setTotalCount(totalCount);
		entity.setSuccessCount(successCount);
		entity.setErrorMsg(errorMsg);
		importTaskMapper.updateById(entity);
	}

	private ImportTaskRecord toRecord(ImportTaskEntity e) {
		return new ImportTaskRecord(
				String.valueOf(e.getId()),
				String.valueOf(e.getUserId()),
				e.getType(),
				String.valueOf(e.getCategoryId()),
				e.getFileName(),
				e.getSourceUrl(),
				e.getStatus(),
				e.getTotalCount(),
				e.getSuccessCount(),
				e.getErrorMsg(),
				e.getCreatedAt(),
				e.getUpdatedAt()
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
