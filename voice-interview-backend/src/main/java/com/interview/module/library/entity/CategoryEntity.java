package com.interview.module.library.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_category")
public class CategoryEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private String name;
	private Long parentId;
	private Integer sortOrder;
	@TableLogic
	private Integer deleted;
}
