-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: redstve
-- ------------------------------------------------------
-- Server version	8.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `category_knowledge`
--

DROP TABLE IF EXISTS `category_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_knowledge` (
  `category_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品类标识符',
  `category_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品类中文名',
  `dynamic_fields` json DEFAULT NULL COMMENT '动态扩展字段矩阵 (dynamicExtensionFields)',
  `prompt_overrides` json DEFAULT NULL COMMENT '该品类专属的分析策略',
  `scene_threshold` double NOT NULL DEFAULT '0.25' COMMENT '该品类的最佳镜头切分阈值',
  `discovered_by_llm` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为大模型自动发现',
  `confidence_score` double NOT NULL DEFAULT '1' COMMENT '品类置信度(0~1)',
  `usage_count` int unsigned NOT NULL DEFAULT '0' COMMENT '被使用/分析命中的次数',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品类结构知识库与最佳阈值表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `category_knowledge`
--

/*!40000 ALTER TABLE `category_knowledge` DISABLE KEYS */;
INSERT INTO `category_knowledge` VALUES ('editing','剪辑类','[{\"fieldName\": \"cut_frequency\", \"fieldType\": \"STRING\", \"description\": \"剪辑密度跨度极大，教学段落中等切频保证信息清晰，高潮段落超高频切镜实现爽感\"}, {\"fieldName\": \"motion_effects\", \"fieldType\": \"JSON\", \"description\": \"该品类常用的镜头运动与特效列表\", \"allowedValues\": [\"speed_ramp\", \"zoom_punch\", \"shake\", \"slow_motion\", \"static_lockoff\", \"extreme_close_up\"]}, {\"fieldName\": \"color_grading\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常见的整体调色风格\"}, {\"fieldName\": \"sync_strategy\", \"fieldType\": \"STRING\", \"description\": \"描述该品类中音画节奏或视觉细节的常见同步策略\"}, {\"fieldName\": \"bpm_range\", \"fieldType\": \"JSON\", \"description\": \"该类高燃混剪内容适配的背景音乐通用节拍范围\"}, {\"fieldName\": \"scene_threshold\", \"fieldType\": \"DOUBLE\", \"description\": \"该品类运行时视频拆解可参考的镜头切分阈值，高于该值判定为高切频混剪片段\"}]',NULL,0.4,0,1,7,'2026-05-25 06:47:48.821','2026-06-02 12:45:23.508'),('marketing','营销类','[{\"fieldName\": \"hook_type\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常见的开头钩子类型\"}, {\"fieldName\": \"hook_options\", \"fieldType\": \"JSON\", \"description\": \"该品类可用的钩子策略候选池\", \"allowedValues\": [\"question\", \"pain_point\", \"data_shock\", \"controversy\"]}, {\"fieldName\": \"selling_point_count\", \"fieldType\": \"INTEGER\", \"description\": \"描述该品类内容中常见的核心卖点覆盖数量\"}, {\"fieldName\": \"cta_type\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常用的行动号召类型\"}, {\"fieldName\": \"social_proof_type\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常见的社会认同表达方式\"}]',NULL,0.3,0,1,0,'2026-05-25 06:47:48.821','2026-06-02 12:45:23.685'),('motion_graphics','MG/动态海报类','[{\"fieldName\": \"animation_style\", \"fieldType\": \"STRING\", \"description\": \"该品类常见的动画表现风格，暗调科技极简风，以低饱和蓝黑配色搭配轻微辉光动效为主\"}, {\"fieldName\": \"text_animations\", \"fieldType\": \"JSON\", \"description\": \"该品类常用的文字动效候选池\", \"allowedValues\": [\"typewriter\", \"slide_in\", \"scale_pop\"]}, {\"fieldName\": \"keyframe_count\", \"fieldType\": \"INTEGER\", \"description\": \"描述该品类常见的关键帧数量规模，平均每1-2秒生成一个展示关键帧\"}, {\"fieldName\": \"loopable\", \"fieldType\": \"BOOLEAN\", \"description\": \"描述该品类内容是否通常适合循环播放\"}, {\"fieldName\": \"color_scheme\", \"fieldType\": \"JSON\", \"description\": \"描述该品类常见的整体配色方案结构\"}, {\"fieldName\": \"scene_threshold\", \"fieldType\": \"DOUBLE\", \"description\": \"该品类运行时视频拆解可参考的镜头切分阈值，高于该值判定为高切频混剪片段\"}]',NULL,0.25,0,1,1,'2026-05-25 06:47:48.821','2026-06-02 12:45:23.864'),('product_review','产品测评','[{\"fieldName\": \"hook_type\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常见的开头注意力抓取方式\"}, {\"fieldName\": \"selling_point_count\", \"fieldType\": \"INTEGER\", \"description\": \"描述该品类内容中常见的核心卖点覆盖数量\"}, {\"fieldName\": \"cta_type\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常用的结尾引导或推荐方式\"}, {\"fieldName\": \"real_shot_ratio\", \"fieldType\": \"DOUBLE\", \"description\": \"描述该品类中实拍内容在整体视频中的常见占比\"}]','{\"scriptAnalysis\": \"数码测评带货类视频必须优先保证实测画面的真实性，所有参数对比内容必须搭配实拍素材，避免纯空泛口播，通过大牌产品和高性价比产品的中立对比突出主推产品优势，可大幅提升用户信任度与转化效率\"}',0.2,1,0.9,2,'2026-05-26 22:53:00.784','2026-06-02 12:45:24.027'),('short_drama','微短剧','[{\"fieldName\": \"cut_frequency\", \"fieldType\": \"STRING\", \"description\": \"描述该品类常见的镜头切换频率范围与剪辑密度特征\"}, {\"fieldName\": \"motion_effects\", \"fieldType\": \"JSON\", \"description\": \"该品类常用的镜头运动与特效候选池\", \"allowedValues\": [\"fast_push_pull\", \"high_speed_tracking_slow_motion\"]}]','{\"scriptAnalysis\": \"优先压缩铺垫叙事时长，直接把冲突密度拉满，用夸张超现实收尾强化用户记忆点\"}',0.25,1,0.9,1,'2026-05-26 22:40:45.507','2026-06-02 12:43:51.784');
/*!40000 ALTER TABLE `category_knowledge` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-07 16:43:36
