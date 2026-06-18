package com.bytedance.aivideo.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bytedance.aivideo.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * ASR 片段实体（用于结果接口聚合 transcript）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asr_segment")
public class AsrSegmentEntity extends BaseEntity {

    private String taskId;

    private Integer segmentIndex;

    private String text;

    private BigDecimal startTime;

    private BigDecimal endTime;

    private String speakerLabel;

    private BigDecimal confidence;

    private String audioEmotion;

    private String volumeIntensity;

    private String backgroundEnvironment;

    private String vocalVibe;

    private String bgmGenre;

    private String bgmInstruments;

    /**
     * 这是一个 MySQL 底层虚拟生成的计算列（Generated Column）。
     * 专门用于配合唯一索引解决逻辑删除冲突问题。
     * Java 代码绝对不能手动去 Insert 或 Update 它。
     */
    @com.baomidou.mybatisplus.annotation.TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Integer activeSegmentIndex;
}
