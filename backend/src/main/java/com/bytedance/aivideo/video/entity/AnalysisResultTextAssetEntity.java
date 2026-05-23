package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频拆解结果文本资产表实体（语义检索层）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_result_text_asset")
public class AnalysisResultTextAssetEntity extends BaseEntity {

    private String taskId;

    private String asrFullText;

    private String ocrFullText;

    private String transcriptSummaryText;

    private String keywordsText;
}
