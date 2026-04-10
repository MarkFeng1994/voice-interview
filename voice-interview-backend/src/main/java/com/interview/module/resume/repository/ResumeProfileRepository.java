package com.interview.module.resume.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.resume.entity.ResumeProfileEntity;
import com.interview.module.resume.mapper.ResumeProfileMapper;
import com.interview.module.resume.service.ResumeProfile;

@Repository
@Profile("dev")
public class ResumeProfileRepository {

	private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
	};

	private final ResumeProfileMapper resumeProfileMapper;
	private final ObjectMapper objectMapper;

	public ResumeProfileRepository(ResumeProfileMapper resumeProfileMapper, ObjectMapper objectMapper) {
		this.resumeProfileMapper = resumeProfileMapper;
		this.objectMapper = objectMapper;
	}

	public ResumeProfile savePendingProfile(String userId, String mediaFileId, String fileName, String mimeType, long sizeBytes) {
		ResumeProfileEntity entity = new ResumeProfileEntity();
		entity.setUserId(Long.parseLong(userId));
		entity.setMediaFileId(mediaFileId);
		entity.setOriginalFileName(fileName);
		entity.setContentType(mimeType);
		entity.setSizeBytes(sizeBytes);
		entity.setParseStatus("UPLOADED");
		resumeProfileMapper.insert(entity);
		return toProfile(entity);
	}

	public ResumeProfile findByResumeId(String userId, String resumeId) {
		return findEntityByResumeId(userId, resumeId)
				.map(this::toProfile)
				.orElseThrow(() -> new IllegalArgumentException("简历不存在"));
	}

	public ResumeProfile markParsing(String userId, String resumeId) {
		ResumeProfileEntity entity = requireEntity(userId, resumeId);
		entity.setParseStatus("PARSING");
		entity.setParseError(null);
		resumeProfileMapper.updateById(entity);
		return toProfile(entity);
	}

	public ResumeProfile markParsed(
			String userId,
			String resumeId,
			String resumeSummary,
			List<String> extractedKeywords,
			List<String> projectHighlights
	) {
		ResumeProfileEntity entity = requireEntity(userId, resumeId);
		entity.setParseStatus("PARSED");
		entity.setResumeSummary(resumeSummary);
		entity.setExtractedKeywords(writeStringList(extractedKeywords));
		entity.setProjectHighlights(writeStringList(projectHighlights));
		entity.setParseError(null);
		resumeProfileMapper.updateById(entity);
		return toProfile(entity);
	}

	public ResumeProfile markFailed(String userId, String resumeId, String parseError) {
		ResumeProfileEntity entity = requireEntity(userId, resumeId);
		entity.setParseStatus("FAILED");
		entity.setResumeSummary(null);
		entity.setExtractedKeywords(writeStringList(List.of()));
		entity.setProjectHighlights(writeStringList(List.of()));
		entity.setParseError(parseError);
		resumeProfileMapper.updateById(entity);
		return toProfile(entity);
	}

	private ResumeProfile toProfile(ResumeProfileEntity entity) {
		return new ResumeProfile(
				String.valueOf(entity.getId()),
				String.valueOf(entity.getUserId()),
				entity.getMediaFileId(),
				entity.getOriginalFileName(),
				entity.getContentType(),
				entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
				entity.getParseStatus(),
				entity.getResumeSummary(),
				readStringList(entity.getExtractedKeywords()),
				readStringList(entity.getProjectHighlights()),
				entity.getParseError()
		);
	}

	private ResumeProfileEntity requireEntity(String userId, String resumeId) {
		return findEntityByResumeId(userId, resumeId)
				.orElseThrow(() -> new IllegalArgumentException("简历不存在"));
	}

	private Optional<ResumeProfileEntity> findEntityByResumeId(String userId, String resumeId) {
		return Optional.ofNullable(resumeProfileMapper.selectOne(
				new LambdaQueryWrapper<ResumeProfileEntity>()
						.eq(ResumeProfileEntity::getId, parseId(resumeId, "resumeId"))
						.eq(ResumeProfileEntity::getUserId, parseId(userId, "userId"))
		));
	}

	private long parseId(String value, String field) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(field + " 格式非法");
		}
	}

	private String writeStringList(List<String> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? List.of() : values);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("简历画像序列化失败", ex);
		}
	}

	private List<String> readStringList(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, STRING_LIST_TYPE);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("简历画像反序列化失败", ex);
		}
	}
}
