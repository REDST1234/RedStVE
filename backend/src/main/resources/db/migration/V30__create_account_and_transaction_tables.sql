-- Flyway V30: 初始化账户积分与流水表
-- 学习点：金融/交易系统的底层双表模型（主表 + 流水表）

-- 1. 用户账户主表（记录当前余额）
CREATE TABLE IF NOT EXISTS user_account (
    -- 核心学习点：分布式唯一 ID（Snowflake 雪花算法）
    id BIGINT PRIMARY KEY COMMENT '分布式雪花算法主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    points INT NOT NULL DEFAULT 0 COMMENT '当前可用积分余额',
    
    -- 核心学习点：乐观锁（Optimistic Locking）字段
    -- 用于防止高并发下的“超扣”问题（例如：两个人同时用同一个账号点击生成视频）
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户积分表';

-- 2. 资金流水表（记录每一笔积分变动）
CREATE TABLE IF NOT EXISTS account_transaction (
    -- 在分布式/分库分表架构中，决不能使用 AUTO_INCREMENT（会导致主键冲突）。
    -- 同时也强烈不建议使用 UUID 或纯业务单号作为主键（因为无序的长字符串会导致 MySQL InnoDB 聚簇索引频繁页分裂，性能极差）。
    -- 最佳实践：由应用层（MyBatis Plus）通过雪花算法生成一个全局唯一、且趋势递增的 BIGINT 作为物理主键。
    id BIGINT PRIMARY KEY COMMENT '分布式雪花算法主键',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    transaction_type VARCHAR(32) NOT NULL COMMENT '交易类型：CONSUME(消耗), REFUND(退还), RECHARGE(充值)',
    amount INT NOT NULL COMMENT '变动金额（无论增减均记录正数，通过类型区分）',
    
    -- 核心学习点：幂等性键（Idempotency Key） / 业务唯一凭证
    -- 在微服务网络中，MQ 消息可能会重发。如果同一个“视频生成任务”重发了两次扣费指令，
    -- 我们依靠这个字段的唯一索引（UNIQUE KEY）来报错拦截，绝对不允许扣两次费。
    biz_reference_id VARCHAR(128) NOT NULL COMMENT '外部业务凭证ID（如：视频生成任务的 TaskId）',
    
    description VARCHAR(255) COMMENT '变动描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    
    -- 联合唯一索引：确保针对同一个业务单据（如同一个视频任务），同一种交易类型（如扣费），只能发生一次！
    UNIQUE KEY uk_biz_ref_type (biz_reference_id, transaction_type),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户积分流水变动表';

-- 插入一条初始测试用户的记录，送他 1000 积分体验金
INSERT INTO user_account (id, user_id, points, version) VALUES (100000001, 'test_user_001', 1000, 0);
