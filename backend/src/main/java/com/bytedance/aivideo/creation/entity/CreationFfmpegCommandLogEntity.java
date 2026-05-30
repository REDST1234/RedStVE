package com.bytedance.aivideo.creation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creation_ffmpeg_command_log")
public class CreationFfmpegCommandLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long bizId;

    private String projectId;

    private String versionId;

    private String matchId;

    private Integer segmentIndex;

    private String materialBizId;

    private String strategyType;

    private String commandText;

    private String status;

    private String vetoReason;

    private String errorMessage;

    private Integer exitCode;

    private Long elapsedMs;

    private String stdoutTail;

    private String stderrTail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
