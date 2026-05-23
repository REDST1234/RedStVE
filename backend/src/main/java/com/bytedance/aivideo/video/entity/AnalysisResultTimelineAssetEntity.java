package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频拆解结果时序资产表实体（冷数据层）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_result_timeline_asset")
public class AnalysisResultTimelineAssetEntity extends BaseEntity {

    private String taskId;

    private String shotSummaryJson;

    private String timelineLogJson;

    private String scriptStructureJson;

    private String rhythmStructureJson;

    private String packagingStructureJson;

    private String llmTokenUsageJson;

    private String provenanceJson;
}
