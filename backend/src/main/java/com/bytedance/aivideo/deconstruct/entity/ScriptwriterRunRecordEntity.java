package com.bytedance.aivideo.deconstruct.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编剧运行记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scriptwriter_run_record")
public class ScriptwriterRunRecordEntity extends BaseEntity {

    private String runId;

    private String projectId;

    private String snapshotId;

    private String materialBizIdsJson;

    private String finalPromptText;

    private String status;

    private String writerOutputJson;

    private String errorMessage;
}
