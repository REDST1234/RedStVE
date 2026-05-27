package com.bytedance.aivideo.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.video.entity.KeyFrameEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关键帧抽取结果 Mapper 接口。
 */
@Mapper
public interface KeyFrameMapper extends BaseMapper<KeyFrameEntity> {
}
