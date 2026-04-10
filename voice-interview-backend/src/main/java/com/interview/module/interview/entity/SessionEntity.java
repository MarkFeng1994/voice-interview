package com.interview.module.interview.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_interview_session")
public class SessionEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private String sessionKey;
	private Long userId;
	private String title;
	private String selectedCategoryIds;
	private String configJson;
	private String interactionMode;
	private String status;
	private Integer currentQuestionIndex;
	private Integer totalQuestionCount;
	private Integer overallScore;
	private String overallComment;
	private LocalDateTime lastActiveAt;
}
