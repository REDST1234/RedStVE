package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("slot_match_result")
public class SlotMatchResultEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String matchId;
    
    private String projectId;
    
    private Integer segmentIndex;
    
    private String segmentRole; // hook/body/climax/outro
    
    private String matchedAssetId;

    private String matchedHighlightId;
    
    private Double matchScore;
    
    private String matchStatus; // MATCHED/PARTIAL/MISSING

    private String matchReason;

    private String vetoReason;
    
    private String versionId;
    
    private String adaptationPlanJson;
    
    private String adaptedFilePath;

    // ---- AI 生图补位字段 ----
    private Boolean imageGenEligible;
    private String imageGenCategory;
    private String imageGenPrompt;
    private String imageGenDescription;
    private String imageGenStatus;
    private String imageGenUrl;
    private String imageGenErrorMessage;

    public boolean isImageGenReady() {
        return Boolean.TRUE.equals(imageGenEligible)
                && "COMPLETED".equals(imageGenStatus)
                && imageGenUrl != null && !imageGenUrl.isBlank();
    }

    public boolean isImageGenPending() {
        return Boolean.TRUE.equals(imageGenEligible)
                && ("PENDING".equals(imageGenStatus) || "PROCESSING".equals(imageGenStatus));
    }

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableLogic
    private LocalDateTime deletedAt;
}
