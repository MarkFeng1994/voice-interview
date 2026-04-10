package com.interview.module.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.module.interview.entity.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}
