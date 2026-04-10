package com.interview.module.resume.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_resume_profile")
public class ResumeProfileEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private String mediaFileId;
	private String originalFileName;
	private String contentType;
	private Long sizeBytes;
	private String parseStatus;
	private String resumeSummary;
	private String extractedKeywords;
	private String projectHighlights;
	private String parseError;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
