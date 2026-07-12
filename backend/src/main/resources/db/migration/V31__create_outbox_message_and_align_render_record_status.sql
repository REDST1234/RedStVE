-- V31: 为 Remotion 渲染可靠性改造预埋 Outbox 数据模型
-- 1) render_record 补齐 CREATED 状态语义
-- 2) 新建 outbox_message 表，承接消息投递状态

ALTER TABLE render_record
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'CREATED'
        COMMENT 'CREATED / QUEUED / RENDERING / DONE / FAILED / GENERATING_SCRIPT / SCRIPT_DONE';

CREATE TABLE IF NOT EXISTS outbox_message (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    message_id     VARCHAR(128) NOT NULL COMMENT '消息全局唯一 ID',
    message_type   VARCHAR(64)  NOT NULL COMMENT '消息类型，例如 RENDER_SUBMIT',
    biz_key        VARCHAR(128) NOT NULL COMMENT '业务键，当前建议使用 renderId',
    payload        LONGTEXT     NOT NULL COMMENT '待投递消息体',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENDING / SENT / DEAD',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '已失败重试次数',
    next_retry_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次允许重试的时间点',
    last_error     VARCHAR(1024) NULL    COMMENT '最近一次失败原因',
    created_at     DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_outbox_message_id (message_id),
    UNIQUE INDEX idx_outbox_type_biz_key (message_type, biz_key),
    INDEX idx_outbox_status_retry_time (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表，承接渲染任务投递状态';
