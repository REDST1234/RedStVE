package com.bytedance.aivideo.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccountEntity {

    // 抛弃旧的自增策略，使用分布式雪花算法
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userId;

    private Integer points;

    // 开启 MyBatis Plus 乐观锁插件
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
