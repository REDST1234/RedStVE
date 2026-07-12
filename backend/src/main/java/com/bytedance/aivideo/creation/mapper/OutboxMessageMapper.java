package com.bytedance.aivideo.creation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.creation.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageEntity> {
}
