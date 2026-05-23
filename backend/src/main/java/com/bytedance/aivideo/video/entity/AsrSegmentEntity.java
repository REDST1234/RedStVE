package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * ASR 片段实体（用于结果接口聚合 transcript）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asr_segment")
public class AsrSegmentEntity extends BaseEntity {

    private String taskId;

    private Integer segmentIndex;

    private String text;

    private BigDecimal startTime;

    private BigDecimal endTime;

    private String speakerLabel;

    private BigDecimal confidence;
}
