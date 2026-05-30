package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.SlotMatchLlmResult;
import com.bytedance.aivideo.creation.entity.CreativeMaterialEntity;

import java.util.List;

public interface SlotMatchLlmService {

    SlotMatchLlmResult matchSlots(
            String projectId,
            String versionId,
            String templateSnapshotJson,
            List<CreativeMaterialEntity> materials
    );
}
