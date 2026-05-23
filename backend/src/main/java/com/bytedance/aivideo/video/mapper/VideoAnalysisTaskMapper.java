package com.bytedance.aivideo.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.aivideo.video.entity.VideoAnalysisTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频拆解任务 Mapper。
 */
@Mapper
public interface VideoAnalysisTaskMapper extends BaseMapper<VideoAnalysisTaskEntity> {
}
