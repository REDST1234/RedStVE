package com.bytedance.aivideo.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account_transaction")
public class AccountTransactionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String userId;

    private String transactionType;

    private Integer amount;

    private String bizReferenceId;

    private String description;

    private LocalDateTime createdAt;
}
