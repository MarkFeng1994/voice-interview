package com.interview.module.media.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_media_file")
public class MediaFileEntity {

	@TableId(type = IdType.AUTO)
	private Long id;
	private Long userId;
	private String bizType;
	private String storageType;
	private String fileKey;
	private String mimeType;
	private Long durationMs;
	private Long sizeBytes;
	private LocalDateTime expireAt;
}
