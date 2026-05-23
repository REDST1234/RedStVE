package com.bytedance.aivideo.deconstruct.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拆解项目实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deconstruct_project")
public class DeconstructProjectEntity extends BaseEntity {

    private String projectId;

    private String title;

    private String description;

    private String tagsJson;

    private String coverUrl;

    private String status;
}
