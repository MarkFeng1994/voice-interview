package com.interview.module.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_question")
public class QuestionEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private Long categoryId;
	private String title;
	private String content;
	private String answer;
	private Integer difficulty;
	private String source;
	private String sourceUrl;
	@TableLogic
	private Integer deleted;
}
