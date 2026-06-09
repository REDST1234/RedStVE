package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("creation_project")
public class CreationProjectEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String projectId;
    
    private String title;
    
    private String description;
    
    private String templateId;

    private String templateSnapshotId;
    
    private String templateSnapshotJson;
    
    private String status; // DRAFT/MATCHING/ADAPTING/COMPOSED/EXPORTED

    private String renderAspectRatio;

    private String latestRenderId;
    
    private String draftScriptJson;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private LocalDateTime deletedAt;
}
