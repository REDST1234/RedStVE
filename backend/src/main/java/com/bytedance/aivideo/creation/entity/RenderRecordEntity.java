package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("render_record")
public class RenderRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String renderId;

    private String projectId;

    private String status; // QUEUED / RENDERING / DONE / FAILED

    private String outputPath;

    private String aspectRatio;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
