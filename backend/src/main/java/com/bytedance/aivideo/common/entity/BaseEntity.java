package com.bytedance.aivideo.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用实体基类。
 * 约定：
 * 1) id 为数据库自增主键
 * 2) bizId 为业务主键（雪花ID）
 * 3) createdAt/updatedAt 由业务写入或数据库默认值维护
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long bizId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除时间：为空表示未删除，非空表示删除时间点。
     */
    private LocalDateTime deletedAt;
}
