package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("category_knowledge")
public class CategoryKnowledgeEntity {

    @TableId
    private String categoryId;

    private String categoryName;

    private String dynamicFields;

    private String promptOverrides;

    private Double sceneThreshold;

    private Boolean discoveredByLlm;

    private Double confidenceScore;

    private Integer usageCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
