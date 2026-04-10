package com.interview.module.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.module.library.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
