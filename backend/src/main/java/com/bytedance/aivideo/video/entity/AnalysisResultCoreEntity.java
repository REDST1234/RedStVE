package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 视频拆解结果核心表实体（高频索引层）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_result_core")
public class AnalysisResultCoreEntity extends BaseEntity {

    private String taskId;

    private String status;

    private String categoryId;

    private String llmModelUsed;

    private String schemaVersion;

    private String partialFailedDimensions;

    private BigDecimal durationSec;

    private Integer width;

    private Integer height;

    private BigDecimal fps;

    private String videoCodec;

    private String audioCodec;

    private Integer hasAudio;

    private Long bitrate;

    private Integer shotCount;

    private Integer asrSegmentCount;

    private Integer keyFrameCount;
}
