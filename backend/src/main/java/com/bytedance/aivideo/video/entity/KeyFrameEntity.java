package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 关键帧抽取结果实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("key_frame")
public class KeyFrameEntity extends BaseEntity {

    private String taskId;

    private Integer frameIndex;

    private BigDecimal timePoint;

    private Integer sourceShotIndex;

    private String extractionReason;

    private String filePath;

    private String description;
}
