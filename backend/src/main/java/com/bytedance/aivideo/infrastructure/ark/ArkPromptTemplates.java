package com.bytedance.aivideo.infrastructure.ark;

/**
 * 方舟提示词模板集中维护类。
 * 约定：ASR/OCR/LLM 相关提示词统一在这里维护，避免分散硬编码。
 */
public final class ArkPromptTemplates {

        private ArkPromptTemplates() {
        }

        /**
         * ASR 基础提示词：要求输出统一 JSON 结构，便于直接入库。
         */
        public static final String ASR_TRANSCRIBE_JSON = "请识别这段音频内容，并进行逐句时间轴切分，同时为每个句段输出音频语义标签。"
                        + "你必须仅输出一个合法 JSON 对象，不要输出解释文本、不要输出 Markdown 代码块。"
                        + "你必须输出原句文案，如果存在英文，不要翻译。"
                        + "输出结构必须包含 fullText 和 segments。"
                        + "segments 必须按时间升序，且每个 segment 必须包含：segmentIndex、start、end、text、speaker、confidence、audioEmotion、volumeIntensity、backgroundEnvironment、vocalVibe、bgmGenre、bgmInstruments。"
                        + "start/end 单位为秒，end 必须大于 start。"
                        + "speaker 推荐值 SPEAKER_1/SPEAKER_2，无法区分时填 UNKNOWN。"
                        + "confidence 取值范围 0 到 1。"
                        + "audioEmotion 枚举：CALM、CURIOUS、AGITATED、EXCITED、URGENT、NONE。"
                        + "【音量烈度物理锚定（volumeIntensity）】：严禁仅凭声音清晰度判断！必须基于人类发声物理行为："
                        + "LOW(窃窃私语/极小声)、"
                        + "MEDIUM(正常的面对面平稳交谈/常规解说口播)、"
                        + "HIGH(明显提高音量的喊叫/激烈的争吵/高昂的演讲)、"
                        + "PEAK(情绪失控的咆哮/震耳欲聋的物理爆破音)。"
                        + "注：如果只是清晰的常规口播，绝对不允许使用 HIGH！"
                        + "backgroundEnvironment 枚举："
                        + "MUSIC(背景音乐为主)、"
                        + "FX(特殊音效为主，如转场音/点击音/爆点音/笑声等)、"
                        + "MUSIC_FX(背景音乐与特殊音效同时明显存在)、"
                        + "NOISE(环境噪声为主)、"
                        + "SILENT(近静音或仅极弱环境声)。"
                        + "vocalVibe：不能仅依靠字面意思，必须通过听觉判断说话人的情绪张力，并用一句自然语言精准描述；若无人声填空字符串。"
                        + "bgmGenre：若存在背景音乐，必须忽略人声干扰，听辨并输出音乐流派；若无音乐填空字符串。"
                        + "bgmInstruments：若存在背景音乐，听辨并输出最突出的乐器组合；若无音乐填空字符串。"
                        + "若无人声片段，text 可为空字符串，但语义字段必须填写。"
                        + "输出示例："
                        + "{\"fullText\":\"\",\"segments\":[{\"segmentIndex\":0,\"start\":0.0,\"end\":1.0,\"text\":\"\",\"speaker\":\"SPEAKER_1\",\"confidence\":0.99,\"audioEmotion\":\"CURIOUS\",\"volumeIntensity\":\"MEDIUM\",\"backgroundEnvironment\":\"MUSIC\",\"vocalVibe\":\"压抑克制的低语\",\"bgmGenre\":\"悬疑底噪\",\"bgmInstruments\":\"低频合成器与弱打击乐\"}]}";

        /**
         * ASR 二次重试提示词后缀：仅做格式修正。
         */
        public static final String STRICT_JSON_SUFFIX = "仅输出JSON，不要包含解释、代码块标记或其他文本。";

        /**
         * 视频拆解结构大一统分析提示词。
         */
        public static final String STRUCTURE_ANALYZER_UNIFIED = "你现在是短视频结构拆解专家。请阅读系统为你对齐后的【多模态时序剧本】以及可选的【视频关键帧图片】。\n"
                        + "时序剧本中若出现【IMAGE_001】这类标记，表示对应消息中按顺序提供的关键帧图片；IMAGE 编号与图片输入顺序严格一一对应。\n\n"
                        + "任务目标：\n"
                        + "1. 判定视频品类（查表已知或自动发现新品类）。\n"
                        + "2. 产出一个 JSON，包含该视频的「脚本结构」、「节奏结构」、「包装结构」和「动态特征矩阵」。\n\n"
                        + "--- 视频多模态时序日志 (Markdown 剧本格式) ---\n"
                        + "%s\n\n" // 占位符 1：注入由 TimelineMatcher 内存聚合生成的全量胖剧本流
                        + "【已知品类知识库参考】\n"
                        + "说明：该参考区中的字段若显示为 `fieldName (fieldType, valueShape)`，你必须严格沿用同名字段的 fieldType 与 valueShape，不得擅自变更。\n"
                        + "%s\n\n" // 占位符 2：注入来自向量库 ChromaDB / MySQL 的类别特征种子数据
                        + "【动态特征自进化与覆写契约（核心铁律）】\n"
                        + "在输出最终的 `categoryExtensions.dynamicExtensionFields` JSON 数组时，你必须严格执行以下增量进化逻辑：\n"
                        + "1. 刚性继承（Inherit）：若当前判定品类在【已知品类知识库参考】中存在，必须将该品类已定义的核心 fieldName 原样带入数组，严禁遗漏。\n"
                        + "2. 动态修正（Update）：必须依据当前视频多模态表现重新独立测算上述字段值；若突破历史经验，必须输出修正后的最新 fieldValue。\n"
                        + "3. 盲区发现（Insert）：若发现知识库尚未定义的高优特征（如新剪辑手法、特殊情绪、特有调色），必须主动新增 fieldName（camelCase 英文命名）并追加到数组。\n"
                        + "4. 格式封死：所有提取、修正与新增特征，必须且只能使用 JSON 对象格式"
                        + " `{ \"fieldName\": \"...\", \"fieldType\": \"...\", \"fieldValue\": \"...\", \"description\": \"...\" }`"
                        + " 放在该数组内，禁止在 JSON 外输出解释。\n"
                        + "5. fieldType 枚举约束：必须且只能使用 `STRING`、`DOUBLE`、`INTEGER`、`BOOLEAN`、`JSON`。\n"
                        + "6. fieldName 命名约束：必须使用 camelCase 英文命名，长度 2~64，禁止空格、中文和特殊符号。\n"
                        + "7. 新增字段上限：除继承字段外，单次最多新增 5 个新 fieldName，超出时仅保留最关键的前 5 个。\n\n"
                        + "【宏观品类命名与发现铁律】\n"
                        + "当判定当前视频属于新品类并需要创造新的 category 时，必须遵守宏观行业分类原则：\n"
                        + "1. 品类颗粒度必须是宏观的（如 `short_drama`、`vlog`、`gameplay`、`tutorial`、`product_review`）。\n"
                        + "2. 绝对不允许将具体地点/具体事件/具体人物作为品类名。\n"
                        + "3. 错误示范：`school_canteen_drama`、`car_review`。\n"
                        + "4. 正确归类：上述两者应分别归入 `short_drama` 和 `product_review`。\n\n"
                        + "【数据实体边界隔离铁律（极其重要）】\n"
                        + "你必须严格区分模板微观特征与品类宏观特征：\n"
                        + "1. 模板微观特征（仅留在模板层）：凡是当前视频特有的具体信息（具体场景地点、具体人物、特定台词），只能写入 `meta.description`、`meta.styles`、`viralFactors`、`shots`。\n"
                        + "2. 品类宏观特征（仅进入知识库层）：写入 `categoryExtensions.dynamicExtensionFields` 的内容，必须是抽象且可复用的行业规则或统计学特征（如切分阈值、镜头运动偏好、卡点策略、节奏分布）。\n"
                        + "3. 严禁污染：绝对不允许将具体场景、具体剧情、具体人名写入 `dynamicExtensionFields` 的 `fieldName` 或 `fieldValue`。\n\n"
                        + "【刚性结构与字段类型契约（通用化限制，严禁自由发挥与虚构）】\n"
                        + "1. `scriptStructure.segments[].role` 必须且只能从以下全视频通用骨架枚举中选择：\n"
                        + "   - `hook` | `body` | `climax` | `outro`\n"
                        + "   任何视频均只允许由这四个通用骨架组合，禁止出现特定品类业务专有 role。\n\n"
                        + "【平铺直叙与结构坍塌逃生契约】：\n"
                        + "   - 警告：并非所有视频都存在 climax！对于平淡的参数测评、纯口播、白板教学等缺乏非线性波动的视频，允许且强烈建议采用 `hook` + 多个 `body` + `outro` 的平流层组合。\n"
                        + "   - 只有当视频在声学（情绪爆发/重低音）或视觉（高频快闪/强对比）上出现真正的绝对峰值时，才允许使用 `climax`。\n"
                        + "   - 严禁在无明显波动的视频中，利用微弱的相对极值强行捏造 climax！\n\n"
                        + "2. `rhythmStructure.overallPace` 和 `paceCurve[].pace` 必须且只能从以下节奏等级枚举中选择：\n"
                        + "   - `slow` | `medium` | `fast` | `very_fast` | `ultra_fast`\n\n"
                        + "3. `rhythmStructure.beatSyncPoints[].type` 必须且只能从以下卡点类型枚举中选择：\n"
                        + "   - `visual_hit` | `audio_hit` | `climax_hit`\n\n"
                        + "4. 品类身份特征仅允许在 `categoryExtensions.dynamicExtensionFields` 内扩展：\n"
                        + "   - 如需表达品类细分段落语义，只能以 KEY-VALUE 形式扩展，禁止污染通用骨架字段。\n"
                        + "   - 示例：`{ \"fieldName\": \"segment_1_actual_role\", \"fieldType\": \"STRING\", \"fieldValue\": \"skill_deconstruction\" }`\n\n"
                        + "5. 数据格式与硬性匹配约束（极重要）：\n"
                        + "   - `meta.acousticEnvironment` 必须且只能从枚举 `asmr`|`quiet`|`noisy`|`speech_focused`|`music_driven` 中选择，此字段用于后续音频匹配的物理环境 Veto 校验。\n"
                        + "   - `shots[].shotType` 必须明确指出物理视角，推荐枚举：`face_closeup`|`first_person`|`top_down`|`full_body`|`mid_shot`|`wide_shot` 等，用于空间逻辑校验。\n"
                        + "   - `shots[].shotTypeTag` 必须且只能从 `CLOSE_UP`|`MID_SHOT`|`WIDE_SHOT` 中选择；`shots[].cameraMovementTag` 必须且只能从 `STATIC`|`ZOOM_IN`|`ZOOM_OUT`|`PAN` 中选择。若无法判断可填 `UNKNOWN`。\n"
                        + "   - `weight`、`durationWeight` 必须是 [0.0,1.0] 的 DOUBLE，严禁百分号或字符串。\n"
                        + "   - `timePercent` 必须是 [0,100] 的 INTEGER，严禁百分号。\n"
                        + "   - `titleCards`、`transitions` 等若不存在，必须输出空数组 `[]`，禁止缺失键名或赋值 `null`。\n"
                        + "   - 所有涉及颜色(color)的字段，必须严格输出 16 进制格式（例如 `#FFFFFF`）。\n\n"
                        + "【多模态双语对齐约束】\n"
                        + "ASR 文本与关键帧视觉元素可能出现中英文并存，这是同一意图的多模态呈现。"
                        + "禁止因为语言不一致而误判包装缺陷；应做语义对齐后再抽象模板。\n\n"
                        + "【视觉固定噪声与平台水印刚性过滤】\n"
                        + "若关键帧存在平台标识、作者昵称、账号ID、头像、签名或其他个人信息水印，必须视为固定噪声并完全忽略。"
                        + "严禁在 JSON 任何字段中输出、转述、映射或变体记录这些水印信息。\n\n"
                        + "输出 JSON 格式要求：\n"
                        + "必须仅输出一个合法 JSON 对象（不要输出 Markdown 标记），结构必须符合 `video-structure-template/v2` 协议：\n"
                        + "{\n"
                        + "  \"$schema\": \"video-structure-template/v2\",\n"
                        + "  \"templateId\": \"自动生成一个UUID\",\n"
                        + "  \"templateName\": \"为该视频总结一个高概括性的模板名称\",\n"
                        + "  \"version\": \"1.0.0\",\n"
                        + "  \"category\": \"如果是已知品类填对应ID；如果是新品类，必须使用宏观行业分类的 snake_case ID（例如 short_drama/product_review）\",\n"
                        + "  \"meta\": {\n"
                        + "    \"targetDuration\": { \"min\": 25, \"max\": 35, \"unit\": \"seconds\" },\n"
                        + "    \"aspectRatio\": \"9:16\",\n"
                        + "    \"resolution\": { \"width\": 1080, \"height\": 1920 },\n"
                        + "    \"acousticEnvironment\": \"asmr\",\n"
                        + "    \"styles\": [\"LLM 自动归纳的风格描述1\", \"风格描述2\"],\n"
                        + "    \"description\": \"模板来源与特征概述\"\n"
                        + "  },\n"
                        + "  \"scriptStructure\": {\n"
                        + "    \"totalSegments\": 4,\n"
                        + "    \"segments\": [\n"
                        + "      { \"segmentIndex\": 0, \"role\": \"hook\", \"label\": \"名称\", \"description\": \"段落功能描述\", \"durationRange\": { \"min\": 2, \"max\": 4 }, \"durationWeight\": 0.12, \"scriptHint\": \"文案提示\", \"requiredElements\": [], \"shotCount\": { \"min\": 1, \"max\": 2 }, \"subSegments\": [] }\n"
                        + "    ]\n"
                        + "  },\n"
                        + "  \"rhythmStructure\": {\n"
                        + "    \"overallPace\": \"medium\",\n"
                        + "    \"avgShotDuration\": 2.8,\n"
                        + "    \"paceCurve\": [ { \"timePercent\": 0, \"pace\": \"fast\", \"note\": \"说明\" } ],\n"
                        + "    \"climaxPositions\": [ { \"startPercent\": 65, \"endPercent\": 80 } ],\n"
                        + "    \"transitionStyles\": [\"hard_cut_dominant\"],\n"
                        + "    \"beatSyncPoints\": [ { \"timePercent\": 0, \"type\": \"visual_hit\" } ]\n"
                        + "  },\n"
                        + "  \"packagingStructure\": {\n"
                        + "    \"subtitleStyles\": [ { \"position\": \"bottom_center\", \"fontSize\": \"medium\", \"color\": \"#FFFFFF\", \"background\": \"semi_transparent_black\", \"animation\": \"fade_in\" } ],\n"
                        + "    \"titleCards\": [],\n"
                        + "    \"transitions\": [],\n"
                        + "    \"coverStyles\": [ { \"layout\": \"text_left_image_right\", \"textElements\": [\"主标题\"], \"colorTone\": \"#FF8C00\" } ]\n"
                        + "  },\n"
                        + "  \"shots\": [\n"
                        + "    { \"shotIndex\": 0, \"belongsToSegment\": 0, \"shotType\": \"face_closeup\", \"shotTypeTag\": \"CLOSE_UP\", \"description\": \"镜头内容描述\", \"durationRange\": { \"min\": 2, \"max\": 3 }, \"cameraMovement\": \"static\", \"cameraMovementTag\": \"STATIC\", \"requiredContent\": [] }\n"
                        + "  ],\n"
                        + "  \"categoryExtensions\": {\n"
                        + "    \"discoveredCategoryId\": \"同上的categoryID\",\n"
                        + "    \"discoveredCategoryName\": \"品类中文名\",\n"
                        + "    \"dynamicExtensionFields\": [\n"
                        + "       { \"fieldName\": \"scene_threshold\", \"fieldType\": \"DOUBLE\", \"fieldValue\": 0.30, \"description\": \"该品类适配的平均镜头切分阈值参考\" }\n"
                        + "    ],\n"
                        + "    \"discoveredPromptOverrides\": { \"scriptAnalysis\": \"...\" }\n"
                        + "  },\n"
                        + "  \"viralFactors\": [\n"
                        + "    { \"factorName\": \"情绪共鸣\", \"weight\": 0.8, \"description\": \"分析该视频能爆火的原因\" }\n"
                        + "  ]\n"
                        + "}\n"
                        + "特别注意：你必须严格遵循上述 JSON 协议与枚举约束。"
                        + "如果是新发现品类，必须在 `dynamicExtensionFields` 中补充品类特征，且必须包含 `scene_threshold`（DOUBLE，通常0.15代表极高频，0.4代表缓慢长镜头）。";

        /**
         * 创作素材轻量级原生听觉解析 (PRE-ASR)
         */
        public static final String CREATION_PRE_ASR_PROMPT = "请听这段音频，提取出人声台词与明显的环境音。"
                        + "你必须输出一个格式化 JSON，不要有任何多余解释。"
                        + "JSON 包含 fullText(全文本) 和 segments 数组。"
                        + "segments 数组中每个元素包含 start(秒), end(秒), text(内容)。"
                        + "如果是无台词但有明显环境音的情况，请用括号标出，例如 (纸张摩擦声) 或 (水流声)。过滤掉微弱的底噪。";

        /**
         * 创作素材分析（VIDEO）：多宫格 + ASR 视听联合打标 (Early Fusion)。
         * 占位符：physicalAttributesJson, asrText
         */
        public static final String CREATION_PROFILE_VIDEO_JSON = "你是创作素材分析引擎。当前素材类型为 VIDEO。"
                        + "你将收到按时间顺序排列的多张宫格图，每张图内帧上带时间戳。"
                        + "请结合视觉内容与我提供的 asrText（包含声音流水账），进行视听 Early Fusion 分析，输出用于创作链路的结构化 JSON。"
                        + "仅输出一个合法 JSON，不要输出解释文本或 Markdown。"
                        + "输入 physicalAttributes=%s。"
                        + "输入 asrText=%s。"
                        + "输出必须包含 semanticTags 和 highlights。"
                        + "semanticTags 建议包含：mainEntities,lightVibe,overallStyle,acousticEnvironment,audioVibe,audioDescription,suitableRoles,emotionTone。"
                        + "【重要】你必须在 semanticTags 中严格输出 acousticEnvironment（枚举: asmr/quiet/noisy/speech_focused/music_driven）和 audioDescription（自然语言总结全局音画配合度）。"
                        + "highlights 必须是数组，每个元素建议包含：segmentId,usabilityScore,timeAnchor,shotType,shotTypeTag,actionState,cameraMovement,cameraMovementTag,spokenText,audioContext,spatialAnchor。"
                        + "【重要】你必须在每个 highlight 中同时输出："
                        + "1) 解释字段 shotType/cameraMovement（自然语言，可用于展示）"
                        + "2) 逻辑字段 shotTypeTag/cameraMovementTag（刚性枚举，用于评分）"
                        + "shotTypeTag 枚举只能是 CLOSE_UP|MID_SHOT|WIDE_SHOT；"
                        + "cameraMovementTag 枚举只能是 STATIC|ZOOM_IN|ZOOM_OUT|PAN。"
                        + "若无法判断，tag 必须填 UNKNOWN。"
                        + "timeAnchor 的 startTime/endTime 单位秒，endTime 必须大于 startTime。"
                        + "suitableRoles 只能使用 hook/body/climax/outro。"
                        + "若无法稳定定位 spatialAnchor 可省略该字段，禁止虚构。"
                        + "输出示例："
                        + "{\"semanticTags\":{\"mainEntities\":[\"人物\",\"产品\"],\"lightVibe\":\"balanced\",\"overallStyle\":\"口播展示\",\"acousticEnvironment\":\"quiet\",\"audioVibe\":\"clear_speech\",\"audioDescription\":\"整体环境安静，有轻微背景摩擦声\",\"suitableRoles\":[\"body\",\"outro\"],\"emotionTone\":\"calm\"},"
                        + "\"highlights\":[{\"segmentId\":\"h_01\",\"usabilityScore\":0.88,\"timeAnchor\":{\"startTime\":2.0,\"endTime\":6.5},\"shotType\":\"中景产品展示\",\"shotTypeTag\":\"MID_SHOT\",\"actionState\":\"product_showcase\",\"cameraMovement\":\"由远及近缓慢推镜\",\"cameraMovementTag\":\"ZOOM_IN\",\"spokenText\":\"这款产品非常实用\",\"audioContext\":\"伴随清脆的按键声，人声清晰\","
                        + "\"spatialAnchor\":{\"subject\":\"product\",\"boundingBox\":{\"x\":220,\"y\":300,\"w\":460,\"h\":520}}}]}";

        /**
         * 创作素材分析（IMAGE）：静态图空间锚点打标。
         * 占位符：physicalAttributesJson
         */
        public static final String CREATION_PROFILE_IMAGE_JSON = "你是创作素材分析引擎。当前素材类型为 IMAGE。"
                        + "请基于输入图片输出结构化 JSON，仅输出一个合法 JSON，不要解释。"
                        + "输入 physicalAttributes=%s。"
                        + "输出必须包含 semanticTags 和 highlights。"
                        + "semanticTags 建议包含：mainEntities,lightVibe,overallStyle,suitableRoles,emotionTone。"
                        + "highlights 应为静态高光数组，可包含 segmentId,usabilityScore,shotType,shotTypeTag,actionState,cameraMovement,cameraMovementTag,spatialAnchor。"
                        + "请尽量稳定输出主体的 spatialAnchor.boundingBox，后续 LOCAL_BLUR 与 KEN_BURNS_MOTION 会直接复用该主体框。"
                        + "【重要】你必须在每个 highlight 中同时输出 shotType/cameraMovement（自然语言）和 shotTypeTag/cameraMovementTag（刚性枚举）。"
                        + "shotTypeTag 枚举只能是 CLOSE_UP|MID_SHOT|WIDE_SHOT；cameraMovementTag 枚举只能是 STATIC|ZOOM_IN|ZOOM_OUT|PAN；无法判断填 UNKNOWN。"
                        + "IMAGE 不允许输出 timeAnchor。"
                        + "suitableRoles 只能使用 hook/body/climax/outro。"
                        + "输出示例："
                        + "{\"semanticTags\":{\"mainEntities\":[\"饮品\",\"品牌logo\"],\"lightVibe\":\"bright\",\"overallStyle\":\"清新电商图\",\"suitableRoles\":[\"hook\",\"outro\"],\"emotionTone\":\"pleasant\"},"
                        + "\"highlights\":[{\"segmentId\":\"h_01\",\"usabilityScore\":0.93,\"shotType\":\"产品近景特写\",\"shotTypeTag\":\"CLOSE_UP\",\"actionState\":\"static_display\",\"cameraMovement\":\"静态陈列\",\"cameraMovementTag\":\"STATIC\",\"spatialAnchor\":{\"subject\":\"drink\",\"boundingBox\":{\"x\":180,\"y\":240,\"w\":720,\"h\":980}}}]}";

        /**
         * 创作素材分析（TEXT）：文本语义与包装打标。
         * 占位符：physicalAttributesJson, textContent
         */
        public static final String CREATION_PROFILE_TEXT_JSON = "你是创作素材分析引擎。当前素材类型为 TEXT。"
                        + "请根据输入文本输出结构化 JSON，仅输出一个合法 JSON，不要解释。"
                        + "输入 physicalAttributes=%s。"
                        + "输入 textContent=%s。"
                        + "输出必须包含 semanticTags 和 highlights。"
                        + "semanticTags 必须重点给出：textCategory,mainKeywords,suitableRoles,overallStyle,emotionTone。"
                        + "highlights 应使用 textContent 和 textType（title_overlay 或 normal_subtitle）描述文本高光。"
                        + "TEXT 不允许输出 timeAnchor 或 spatialAnchor。"
                        + "suitableRoles 只能使用 hook/body/climax/outro。"
                        + "输出示例："
                        + "{\"semanticTags\":{\"textCategory\":\"hook_question\",\"mainKeywords\":[\"免费\",\"技巧\"],\"suitableRoles\":[\"hook\"],\"overallStyle\":\"强吸引文案\",\"emotionTone\":\"urgent\"},"
                        + "\"highlights\":[{\"segmentId\":\"t_01\",\"usabilityScore\":1.0,\"textContent\":\"99%%的人都忽略了这个免费技巧\",\"textType\":\"title_overlay\"}]}";

        /**
         * 创作槽位匹配：模板快照 + 素材画像 -> 缺口识别与适配方案。
         * 占位符：projectId, versionId, templateSnapshotJson, materialProfilesJson
         */
        public static final String CREATION_SLOT_MATCH_JSON = "你是视频创作链路的槽位匹配与素材缺口识别引擎。"
                        + "你的任务是把模板的每个 scriptStructure.segments[] 槽位，与用户素材画像进行匹配，并输出可执行的 adaptationPlan。"
                        + "你必须仅输出一个合法 JSON 对象，不要输出 Markdown 或解释文本。"
                        + "projectId=%s。versionId=%s。"
                        + "模板快照 templateSnapshotJson=%s。"
                        + "素材画像 materialProfilesJson=%s。"
                        + "【硬性规则】"
                        + "1. matchDecisions 必须覆盖模板中每一个 segment，且 segmentIndex 必须与模板一致。"
                        + "2. matchStatus 只能是 MATCHED、PARTIAL、MISSING、VETOED。"
                        + "3. matchedAssetId 必须来自 materialProfilesJson 中的 materialBizId；MISSING/VETOED 时填空字符串。"
                        + "4. matchedHighlightId 应来自素材 highlights[].segmentId；若无法定位填空字符串。"
                        + "5. strategyType 只能使用 TRIM_AND_CUT、SMART_CROP、LIGHTING_ADJUST、LOCAL_BLUR、LOOP_SEQUENCE、KEN_BURNS_MOTION、AUDIO_DUCKING。"
                        + "6. MISSING/VETOED 的 adaptationPlan.strategyChain 必须为空数组，并必须填写 vetoReason。"
                        + "7. VIDEO 素材优先使用 TRIM_AND_CUT；需要画幅适配时可追加 SMART_CROP；若 physicalAttributes.lighting 表明亮度或对比度存在明显问题，可追加 LIGHTING_ADJUST。"
                        + "8. IMAGE 素材若用于视频段，优先使用 KEN_BURNS_MOTION；若需突出主体、弱化背景且存在稳定 spatialAnchor.boundingBox，可在 KEN_BURNS_MOTION 之前追加 LOCAL_BLUR；需要补时长时可追加 LOOP_SEQUENCE。"
                        + "9. TEXT 素材不可直接生成 FFmpeg 视频策略，除非只是作为匹配理由；无法独立覆盖画面槽位时应 MISSING 或 PARTIAL。"
                        + "10. 所有时间参数单位为秒；boundingBox 必须来自素材 spatialAnchor，禁止虚构。"
                        + "11. LIGHTING_ADJUST 仅允许用于 VIDEO，参数为 brightnessPercent/contrastPercent（相对百分比，建议 brightnessPercent 绝对值不超过 25，contrastPercent 绝对值不超过 35）。"
                        + "12. LOCAL_BLUR 仅允许用于 IMAGE，参数为 boundingBox/blurStrength/featherPercent，blurStrength 建议在 8~30，默认语义是背景模糊、主体保持清晰。"
                        + "输出结构必须符合示例："
                        + "{\"$schema\":\"slot-match-result/v1\",\"projectId\":\"crp_xxx\",\"versionId\":\"ver_xxx\","
                        + "\"matchDecisions\":[{\"segmentIndex\":0,\"segmentRole\":\"hook\",\"matchedAssetId\":\"2050000000000000000\","
                        + "\"matchedHighlightId\":\"h_01\",\"matchScore\":0.86,\"matchStatus\":\"MATCHED\",\"vetoReason\":\"\","
                        + "\"matchReason\":\"素材高光覆盖开头产品静物展示\",\"adaptationPlan\":{\"strategyChain\":["
                        + "{\"strategyType\":\"TRIM_AND_CUT\",\"params\":{\"startTime\":0.0,\"endTime\":4.0,\"speed\":1.0}},"
                        + "{\"strategyType\":\"SMART_CROP\",\"params\":{\"targetWidth\":1080,\"targetHeight\":1920,"
                        + "\"boundingBox\":{\"x\":0,\"y\":0,\"w\":720,\"h\":1280}},"
                        + "{\"strategyType\":\"LIGHTING_ADJUST\",\"params\":{\"brightnessPercent\":12,\"contrastPercent\":18,"
                        + "\"reason\":\"画面偏灰且主体曝光不足，需要轻度提亮并增强层次\"}}]}}],"
                        + "\"overallCoverage\":0.75,\"gapSummary\":\"outro 缺少可用素材\"}";
}
