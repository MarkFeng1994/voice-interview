package com.interview.module.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_interview_question")
public class SessionQuestionEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long sessionId;
	private Long questionId;
	private Integer questionIndex;
	private Long categoryId;
	private String titleSnapshot;
	private String contentSnapshot;
	private String answerSnapshot;
	private Integer difficultySnapshot;
	private String sourceSnapshot;
}
