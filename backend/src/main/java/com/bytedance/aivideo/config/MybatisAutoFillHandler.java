package com.bytedance.aivideo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器：
 * 1) 插入时自动生成 bizId（雪花ID）
 * 2) 自动维护 createdAt/updatedAt
 */
@Component
public class MybatisAutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long bizId = (Long) getFieldValByName("bizId", metaObject);
        if (bizId == null) {
            setFieldValByName("bizId", IdWorker.getId(), metaObject);
        }

        LocalDateTime createdAt = (LocalDateTime) getFieldValByName("createdAt", metaObject);
        if (createdAt == null) {
            setFieldValByName("createdAt", now, metaObject);
        }

        LocalDateTime updatedAt = (LocalDateTime) getFieldValByName("updatedAt", metaObject);
        if (updatedAt == null) {
            setFieldValByName("updatedAt", now, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
