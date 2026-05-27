📏 一、 空间与画幅归一化（Spatial Normalization）
痛点：用户传了 16:9 的横屏视频，但模板是 9:16 的竖屏短视频。如果直接强扭，画面会被挤压成“面条人”，FFmpeg 直接报错分辨率不匹配。

ScaleAndBlurPadStrategy (等比缩放+高斯模糊填充底图)

作用：短视频领域最经典的兜底法。把横屏视频等比缩小放在屏幕中间，上下留黑的地方用视频自身的高斯模糊做背景填充。

FFmpeg 算子：split, scale, boxblur, overlay。

SmartCropStrategy (居中/智能裁剪)

作用：如果用户的素材分辨率足够大（如 4K 横屏），直接一刀切出中间的 9:16 区域，保证画面填满屏幕，视觉冲击力最强。

FFmpeg 算子：crop=w:h:x:y。

⏱️ 二、 时间轴与流速控制（Temporal Control）
痛点：目标卡点需要 3 秒，用户的素材只有 1 秒，或者长达 10 秒。必须在物理维度强行对齐。

TrimAndCutStrategy (绝对时间轴裁切)

作用：最基础的剪刀。从用户 10 秒的素材里，精准扣出需要的 2.5 秒。

FFmpeg 算子：trim=start=X:end=Y, setpts=PTS-STARTPTS。

SpeedRampStrategy (变速与抽插帧)

作用：素材只有 2 秒，但坑位需要 3 秒。通过 0.66x 慢动作播放强行撑满时间；或者将长镜头加速（延时摄影感）。

FFmpeg 算子：视频流用 setpts，音频流用 atempo。

LoopSequenceStrategy (无缝循环兜底)

作用：用户传了一个 1 秒的“倒咖啡”短片段，坑位需要 5 秒。系统自动将其“正放-倒放-正放”无缝拼接，形成鬼畜或持续动作。

FFmpeg 算子：loop 或 reverse 结合 concat。

🪄 三、 2D 动效与物理伪装（Motion & Camouflage）
痛点：如何把枯燥的静态图片（Image）或者极度死板的固定镜头，变成有呼吸感的“伪视频”。

KenBurnsMotionStrategy (缓慢推拉摇移)

作用：给静态的商品主图加上极慢的 Zoom In（推镜头）或 Pan（平移）。这是把图片资产转化为视频资产的唯一合法途径。

FFmpeg 算子：极其复杂的 zoompan（就是咱们之前写死的那个数学公式）。

🎧 四、 声学隔离与融合（Audio Isolation & Mixing）
痛点：散装视频往往自带极度刺耳的杂音（风声、嘈杂人声），如果不处理，合成后会和咱们精心挑选的顶级 BGM 打架。

AudioMuteStrategy (强制物理静音)

作用：最粗暴也最有效的策略。当大模型判定用户素材没有“核心台词”时，直接把原视频的音轨抽干，只留画面。

FFmpeg 算子：丢弃音频流（-an），或者 volume=0。

AudioDuckingStrategy (智能闪避/压限)

作用：如果用户素材是一段精美的口播，咱们需要保留原声。当口播响起时，BGM 自动降低音量（降至 20%）；口播结束，BGM 音量瞬间恢复。这是专业混音的核心。

FFmpeg 算子：sidechaincompress 或 amix 配合权重。
FFmpeg 算子：sidechaincompress 或 amix 配合权重。