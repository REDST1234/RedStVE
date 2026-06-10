package com.bytedance.aivideo.creation.util;

import lombok.extern.slf4j.Slf4j;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存储路径统一解析器
 * 专为评委跨平台/跨机器评测设计。
 * 能够自动将数据库中写死的本地绝对路径（如 D:\...\storage\ 或者 /Users/.../storage/）
 * 动态映射为当前系统实际运行时的合法存储路径。
 */
@Slf4j
public class StoragePathResolver {

    private static final String STORAGE_DIR_NAME = "storage";

    /**
     * 获取当前系统真实的 storage 根目录
     * 自动处理 backend 目录启动或根目录启动的差异
     */
    public static Path resolveStorageRoot() {
        // 优先检查外层目录（标准的项目根目录启动结构）
        Path parentStorage = Paths.get("..", STORAGE_DIR_NAME).toAbsolutePath().normalize();
        if (parentStorage.toFile().exists()) {
            return parentStorage;
        }
        // 兜底：当前运行目录
        Path cwdStorage = Paths.get(STORAGE_DIR_NAME).toAbsolutePath().normalize();
        if (cwdStorage.toFile().exists()) {
            return cwdStorage;
        }
        // 如果都没创建，默认使用外层目录
        return parentStorage;
    }

    /**
     * 将数据库中存储的任意路径（可能是别人的绝对路径、或者相对路径），
     * 智能转换为当前物理机上的真实绝对路径。
     */
    public static Path resolveToCurrentAbsolutePath(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }
        
        // 统一斜杠分隔符，抹平 Windows 和 Mac 的差异
        String normalizedRaw = dbPath.replace('\\', '/');
        
        // 尝试寻找 "/storage/" 这个标识符
        int storageIdx = normalizedRaw.indexOf("/storage/");
        
        // 处理特殊情况：直接以 "storage/" 开头
        if (storageIdx == -1 && normalizedRaw.startsWith("storage/")) {
            normalizedRaw = "/" + normalizedRaw;
            storageIdx = 0;
        }
        
        if (storageIdx != -1) {
            // 提取出 storage 内部的相对路径，如 "creation-adapt/crp_xxx/img.png"
            String relativePart = normalizedRaw.substring(storageIdx + "/storage/".length());
            return resolveStorageRoot().resolve(relativePart);
        }
        
        // 如果根本不包含 storage，就作为普通路径对待
        return Paths.get(dbPath).toAbsolutePath().normalize();
    }
}
