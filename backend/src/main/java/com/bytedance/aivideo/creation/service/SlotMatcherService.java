package com.bytedance.aivideo.creation.service;

public interface SlotMatcherService {
    
    /**
     * 触发项目模板与资产的智能槽位匹配
     * @param projectId 创作项目ID
     */
    void matchSlots(String projectId);
}
