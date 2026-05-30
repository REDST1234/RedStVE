package com.bytedance.aivideo.engine.strategy;

/**
 * 策略执行后的产物类型，用于编排层决定中间文件扩展名与下一步输入形态。
 */
public enum StrategyOutputKind {
    IMAGE_OUTPUT,
    VIDEO_OUTPUT
}
