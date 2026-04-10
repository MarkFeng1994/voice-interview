package com.interview.module.media.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.module.media.entity.MediaFileEntity;
import com.interview.module.media.mapper.MediaFileMapper;
import com.interview.module.media.service.MediaFileRecord;

@Repository
@Profile("dev")
public class MediaFileRecordRepository {

	private final MediaFileMapper mediaFileMapper;

	public MediaFileRecordRepository(MediaFileMapper mediaFileMapper) {
		this.mediaFileMapper = mediaFileMapper;
	}

	public MediaFileRecord save(String userId, String bizType, String storageType, String fileKey,
			String mimeType, long durationMs, long sizeBytes, LocalDateTime expireAt) {
		MediaFileEntity entity = new MediaFileEntity();
		entity.setUserId(userId == null || userId.isBlank() ? null : Long.parseLong(userId));
		entity.setBizType(bizType);
		entity.setStorageType(storageType);
		entity.setFileKey(fileKey);
		entity.setMimeType(mimeType);
		entity.setDurationMs(durationMs);
		entity.setSizeBytes(sizeBytes);
		entity.setExpireAt(expireAt);
		mediaFileMapper.insert(entity);
		return toRecord(entity);
	}

	public List<MediaFileRecord> findExpired(LocalDateTime now, int limit) {
		return mediaFileMapper.selectList(
				new LambdaQueryWrapper<MediaFileEntity>()
						.isNotNull(MediaFileEntity::getExpireAt)
						.le(MediaFileEntity::getExpireAt, now)
						.orderByAsc(MediaFileEntity::getExpireAt)
						.orderByAsc(MediaFileEntity::getId)
						.last("LIMIT " + limit)
		).stream().map(this::toRecord).toList();
	}

	public void deleteById(String id) {
		mediaFileMapper.deleteById(Long.parseLong(id));
	}

	private MediaFileRecord toRecord(MediaFileEntity e) {
		return new MediaFileRecord(
				String.valueOf(e.getId()),
				e.getUserId() == null ? null : String.valueOf(e.getUserId()),
				e.getBizType(),
				e.getStorageType(),
				e.getFileKey(),
				e.getMimeType(),
				e.getDurationMs(),
				e.getSizeBytes(),
				e.getExpireAt()
		);
	}
}
