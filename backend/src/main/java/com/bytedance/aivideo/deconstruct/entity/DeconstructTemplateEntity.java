package com.bytedance.aivideo.deconstruct.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 全局拆解模板资产实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deconstruct_template")
public class DeconstructTemplateEntity extends BaseEntity {

    private String templateId;

    private Integer templateVersion;

    private String templateName;

    private String categoryId;

    private String status;

    private String sourceTaskId;

    private String templateJson;

    private String snapshotHash;
}
