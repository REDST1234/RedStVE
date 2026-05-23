package com.bytedance.aivideo.deconstruct.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 拆解项目与素材关联实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("deconstruct_project_material")
public class DeconstructProjectMaterialEntity extends BaseEntity {

    private String projectId;

    private Long materialBizId;

    private String taskId;

    private String relationType;

    private Integer sortOrder;
}
