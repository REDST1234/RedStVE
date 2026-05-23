package com.bytedance.aivideo.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskStageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务阶段状态 Mapper。
 */
@Mapper
public interface VideoAnalysisTaskStageMapper extends BaseMapper<VideoAnalysisTaskStageEntity> {
}

