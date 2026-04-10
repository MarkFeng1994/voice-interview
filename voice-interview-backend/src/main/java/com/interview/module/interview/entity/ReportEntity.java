package com.interview.module.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_interview_report")
public class ReportEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long sessionId;
	private Integer overallScore;
	private String overallComment;
	private String reportJson;
	private String reportVersion;
}
