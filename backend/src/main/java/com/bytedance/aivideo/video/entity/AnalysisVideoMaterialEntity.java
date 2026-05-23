package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 拆解流视频素材表实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_video_material")
public class AnalysisVideoMaterialEntity extends BaseEntity {
    private String originalFileName;

    private String filePath;

    private Long fileSize;

    private BigDecimal duration;

    private Integer width;

    private Integer height;

    private String format;

    private String status;
}
