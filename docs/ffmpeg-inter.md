## 🧭 一、 FFmpeg 在项目中的 4 大核心物理应用

在你的单体后端里，FFmpeg 绝不是一个简单的“格式转换工具”，它是你整个多模态感知和原子化重组的“物理触手”。它干的每一件事，都在为大模型输送最宝贵的炮弹：

Plaintext

```
               ┌──> 1. 音频流剥离 ──> 语音 ASR ───┐
               │                                  │
[样例 MP4 视频] ├──> 2. 场景变动检测 ──> 镜头节奏 ──┼─> [归一化多模态对齐] ──> 喂给豆包 LLM
               │                                  │
               └──> 3. 音频响度分析 ──> 视觉冲击 ──┘
```

1. 

   语音维度：音频流物理剥离（任务1、2 ）  

   - **动作**：一秒钟抽干视频里的音频轨，压缩成轻量级 MP3。

   - 

     **目的**：送去给火山方舟 ASR 或者是本地 Whisper 做时间轴转写，拿到基础台词骨架 。  

2. 

   节奏维度：场景变动检测（任务2：节奏结构 ）  

   - **动作**：通过 `scene filter` 色彩直方图变动算法，把视频里发生物理剪辑切镜头的绝对秒数炸出来。

   - 

     **目的**：算出爆款样例的“镜头切换频率”，让大模型现学它是慢节奏营销还是快节奏剪辑 。  

3. 

   包装维度：音频瞬间响度检测（攻克纯视觉冲击死角 ）  

   - **动作**：利用 `ebur128` 过滤器分析音频能量均方根（RMS）波峰。
   - **目的**：捕捉“没有台词、但有音效轰鸣和大字飞入”的黄金卡点，把玄学特效变成“响度突变信号”偷渡给 LLM。

4. 

   生成维度：原子化素材粗剪（任务4：成片 Demo ）  

   - **动作**：明天下午如果想硬搞一个 15 秒的粗糙成片 Demo，FFmpeg 负责无损截取用户上传素材的特定片段（无缝卡点 Hook/Body/CTA），并执行最简单的无缝拼接。

## 🛠️ 二、 工业级 FFmpeg 异步集成方案（Spring Boot + RabbitMQ）

因为 Q9500 只有 4 核 4 线程，物理视频解码极度吃 CPU。如果直接在 Controller 里用同步线程调 FFmpeg，明天只要你在教室里连点三下鼠标，你的服务器就会当场死锁失联。

我们必须使用 **RabbitMQ 的多级流水线 + 进程阻塞缓冲区重定向** 方案，确保整个架构的工程质量。

### 1. 物理层：防止缓冲区死锁的硬核执行器

在 `com.bdc.engine.service` 下建立 `FFmpegExecutor.java`：

Java

```
package com.bdc.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.List;

@Slf4j
@Component
public class FFmpegExecutor {

    /**
     * 硬核执行 FFmpeg 物理命令
     * @param commands 命令行参数列表
     */
    public void execute(List<String> commands) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            // 🚨 致命细节：必须将错误流和标准流合并！
            // FFmpeg 的所有进度日志默认都是吐在 stderr 里的。如果不合并且不用独立线程读出，
            // 操作系统缓存区一旦满了（通常 64KB），整个 Java 进程就会永久死锁卡死！
            builder.redirectErrorStream(true);
            
            Process process = builder.start();

            // 默默把 FFmpeg 的日志流读完，释放操作系统缓冲区
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 测试时可以打开，正式压测时建议关闭以节省磁盘 I/O
                    // log.debug("[FFmpeg Output] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg 执行失败，退出码: " + exitCode);
            }
            log.info("⚡ FFmpeg 任务物理闭环！耗时: {} ms", (System.currentTimeMillis() - startTime));
            
        } catch (Exception e) {
            log.error("❌ FFmpeg 物理执行遭遇毁灭性报错:", e);
            throw new RuntimeException(e);
        }
    }
}
```

### 2. 管道层：物理指令工厂契约

在 `com.bdc.engine.utils` 下建立 `FFmpegCommandFactory.java`，把你的业务脑中想的所有多模态特征，固化为绝对正确的 FFmpeg 黄金物理命令：

Java

```
package com.bdc.engine.utils;

import java.util.*;

public class FFmpegCommandFactory {

    /**
     * 1. 剥离音频流命令
     */
    public static List<String> buildExtractAudioCmd(String videoPath, String outputPath) {
        return Arrays.asList("ffmpeg", "-y", "-i", videoPath, "-f", "mp3", "-vn", outputPath);
    }

    /**
     * 2. 场景切镜头切片变动检测命令（阈值 0.4 代表色彩直方图剧变）
     */
    public static List<String> buildSceneDetectCmd(String videoPath) {
        return Arrays.asList(
            "ffmpeg", "-i", videoPath, 
            "-vf", "select='gt(scene,0.4)',metadata=print", 
            "-f", "null", "-"
        );
    }

    /**
     * 3. 音频瞬间响度（卡点能量波峰）检测命令
     */
    public static List<String> buildLoudnessDetectCmd(String videoPath) {
        return Arrays.asList("ffmpeg", "-i", videoPath, "-filter_complex", "ebur128=video=0", "-f", "null", "-");
    }

    /**
     * 4. 极致无损秒切素材（明天生成 12 秒 Showcase 用）
     */
    public static List<String> buildCutVideoCmd(String videoPath, double start, double duration, String outputPath) {
        return Arrays.asList(
            "ffmpeg", "-y", "-ss", String.valueOf(start), 
            "-i", videoPath, "-t", String.valueOf(duration), 
            "-c:v", "copy", "-c:a", "copy", outputPath
        );
    }
}
```

3. 调度层：RabbitMQ 异步削峰消费者（保命关键 ）  

在 `com.bdc.engine.mq` 下建立 `VideoParseConsumer.java`。配合我们**昨晚锁死的 `prefetch = 1` 倾听评委教诲配置**，死死守住 Q9500 的内存防线 ：  

Java

```
package com.bdc.engine.mq;

import com.bdc.engine.service.FFmpegExecutor;
import com.bdc.engine.utils.FFmpegCommandFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
public class VideoParseConsumer {

    @Autowired
    private FFmpegExecutor ffmpegExecutor;

    // 🎯 昨晚教你的：直接使用方案 B 注解动态创建交换机和队列
    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "bdc.video.parse.queue", durable = "true"),
        exchange = @Exchange(value = "bdc.video.exchange", type = "direct"),
        key = "video.action.parse"
    ))
    public void handleVideoParseTask(String videoPath) {
        log.info("📥 [RabbitMQ] 领到多模态解析任务，视频物理路径: {}", videoPath);
        
        String audioOutputPath = videoPath.replace(".mp4", ".mp3");
        
        // 1. 物理剥离音频
        List<String> extractAudioCmd = FFmpegCommandFactory.buildExtractAudioCmd(videoPath, audioOutputPath);
        log.info("▶ 正在剥离音频流...");
        ffmpegExecutor.execute(extractAudioCmd);
        
        // 2. 场景切换节奏探测
        List<String> sceneDetectCmd = FFmpegCommandFactory.buildSceneDetectCmd(videoPath);
        log.info("▶ 正在探测物理镜头节奏结构...");
        ffmpegExecutor.execute(sceneDetectCmd);
        
        // 3. 后面这里写：拿到的数据丢给 Redis/MySQL，并触发下一级大模型对齐队列...
        log.info("✅ [RabbitMQ] 该视频特征抽取全部物理闭环！");
    }
}
```