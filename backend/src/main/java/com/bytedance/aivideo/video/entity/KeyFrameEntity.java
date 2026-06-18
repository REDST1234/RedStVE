package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 关键帧抽取结果实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("key_frame")
public class KeyFrameEntity extends BaseEntity {

    private String taskId;

    private Integer frameIndex;

    private BigDecimal timePoint;

    private Integer sourceShotIndex;

    private String extractionReason;

    private String filePath;

    private String description;

    /**
     * 这是一个 MySQL 底层虚拟生成的计算列（Generated Column）。
     * 专门用于配合唯一索引解决逻辑删除冲突问题。
     * Java 代码绝对不能手动去 Insert 或 Update 它。
     */
    @com.baomidou.mybatisplus.annotation.TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Integer activeFrameIndex;
}
