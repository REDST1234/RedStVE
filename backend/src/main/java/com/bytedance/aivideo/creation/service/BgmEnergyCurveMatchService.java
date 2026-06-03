package com.bytedance.aivideo.creation.service;

public interface BgmEnergyCurveMatchService {
    /**
     * 计算 BGM 与模板的能量曲线匹配度
     * @param audioDataJson BGM 音频元数据 JSON 字符串
     * @param templateJson 模板结构 JSON 字符串
     * @return 匹配度得分 [0.0, 1.0]，若触发 Hard Veto 则返回 -1.0
     */
    double calculateEnergyCurveScore(String audioDataJson, String templateJson);
}
