package com.interview.module.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.module.library.entity.QuestionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<QuestionEntity> {
}
