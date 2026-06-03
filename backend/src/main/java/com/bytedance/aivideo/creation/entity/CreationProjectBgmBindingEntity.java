package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creation_project_bgm_binding")
public class CreationProjectBgmBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long bizId;

    private String projectId;

    private String versionId;

    private String audioId;

    private String audioName;

    private String srcPath;

    private String previewUrl;

    private String sourceType;

    private Double recommendScore;

    private Double semanticScore;

    private Double energyCurveScore;

    private Double durationBpmScore;

    private String mixLevel;

    private Double volume;

    private Boolean loopEnabled;

    private Integer fadeInFrames;

    private Integer fadeOutFrames;

    private Boolean duckingEnabled;

    private Double duckingRatio;

    private String metadataJson;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
