package com.interview.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class UserEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private String username;
	private String password;
	private String nickname;
	@TableLogic
	private Integer deleted;
}
