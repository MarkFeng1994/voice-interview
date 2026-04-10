package com.interview.module.library.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_import_task")
public class ImportTaskEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private String type;
	private Long categoryId;
	private String fileName;
	private String sourceUrl;
	private String status;
	private Integer totalCount;
	private Integer successCount;
	private String errorMsg;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
