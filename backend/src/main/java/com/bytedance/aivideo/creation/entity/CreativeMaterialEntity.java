package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("creative_material")
public class CreativeMaterialEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long bizId; // 雪花算法业务主键
    
    private String projectId; // 关联的创作项目ID
    
    private String originalFileName;
    
    private String materialType; // TEXT/IMAGE/VIDEO
    
    private String filePath;
    
    private String textContent;
    
    private Long fileSize;
    
    private Double duration;
    
    private Integer width;
    
    private Integer height;
    
    private String format;
    
    private String tags; // JSON
    
    private String description;
    
    private String status; // UPLOADED/PROFILING/PROFILED/FAILED
    
    private String profileJson; // 三层资产档案 JSON
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private LocalDateTime deletedAt;
}
