package com.interview.module.interview.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_interview_round")
public class RoundEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long sessionId;
	private Long interviewQuestionId;
	private Integer questionIndex;
	private Integer followUpIndex;
	private String roundType;
	private String userAnswerMode;
	private String finalUserAnswerText;
	private String userAudioUrl;
	private String aiMessageText;
	private String ttsAudioUrl;
	private Integer score;
	private Long durationMs;
	private LocalDateTime createdAt;
	private LocalDateTime answeredAt;
}
