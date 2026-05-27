package com.bytedance.aivideo.creation.service;

public interface AssetProfilerService {
    
    /**
     * 触发资产解析管线 (Layer 1, Layer 2, Layer 3)
     * @param assetId 资产ID
     */
    void profileAsset(String assetId);
}
