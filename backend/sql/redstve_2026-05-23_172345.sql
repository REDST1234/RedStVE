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
-- Table structure for table `analysis_result_core`
--

DROP TABLE IF EXISTS `analysis_result_core`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_result_core` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(一对一)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED' COMMENT '结果状态: PENDING/PROCESSING/COMPLETED/FAILED/PARTIAL_SUCCESS',
  `category_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LLM判定品类ID',
  `llm_model_used` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的LLM模型标识',
  `schema_version` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v2' COMMENT '结构Schema版本',
  `partial_failed_dimensions` json DEFAULT NULL COMMENT '局部失败维度(JSON数组)',
  `duration_sec` decimal(10,3) DEFAULT NULL COMMENT '视频时长(秒)',
  `width` smallint unsigned DEFAULT NULL COMMENT '视频宽度',
  `height` smallint unsigned DEFAULT NULL COMMENT '视频高度',
  `fps` decimal(10,3) DEFAULT NULL COMMENT '帧率',
  `video_codec` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频编码',
  `audio_codec` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '音频编码',
  `has_audio` tinyint(1) DEFAULT NULL COMMENT '是否含音频(0/1)',
  `bitrate` bigint unsigned DEFAULT NULL COMMENT '视频码率',
  `shot_count` int unsigned NOT NULL DEFAULT '0' COMMENT '镜头数量',
  `asr_segment_count` int unsigned NOT NULL DEFAULT '0' COMMENT 'ASR片段数量',
  `key_frame_count` int unsigned NOT NULL DEFAULT '0' COMMENT '关键帧数量',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_result_core_biz_id` (`biz_id`),
  UNIQUE KEY `uk_analysis_result_core_task_id` (`task_id`),
  KEY `idx_core_status_created` (`status`,`created_at`),
  KEY `idx_core_category` (`category_id`),
  KEY `idx_core_task_status` (`task_id`,`status`),
  CONSTRAINT `chk_analysis_result_core_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'COMPLETED',_utf8mb4'FAILED',_utf8mb4'PARTIAL_SUCCESS')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解结果核心状态表(高频索引层)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `analysis_result_text_asset`
--

DROP TABLE IF EXISTS `analysis_result_text_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_result_text_asset` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(一对一)',
  `asr_full_text` longtext COLLATE utf8mb4_unicode_ci COMMENT 'ASR全文(脱水文本)',
  `ocr_full_text` longtext COLLATE utf8mb4_unicode_ci COMMENT 'OCR全文(脱水文本)',
  `transcript_summary_text` longtext COLLATE utf8mb4_unicode_ci COMMENT '转写摘要文本',
  `keywords_text` text COLLATE utf8mb4_unicode_ci COMMENT '关键词文本(逗号分隔或自然语言)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_result_text_biz_id` (`biz_id`),
  UNIQUE KEY `uk_analysis_result_text_task_id` (`task_id`),
  FULLTEXT KEY `ft_text_semantic` (`asr_full_text`,`ocr_full_text`,`transcript_summary_text`,`keywords_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解文本语义资产表(全文检索层)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `analysis_result_timeline_asset`
--

DROP TABLE IF EXISTS `analysis_result_timeline_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_result_timeline_asset` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(一对一)',
  `shot_summary_json` json DEFAULT NULL COMMENT '镜头摘要JSON',
  `timeline_log_json` json DEFAULT NULL COMMENT '多模态时序日志JSON',
  `script_structure_json` json DEFAULT NULL COMMENT '脚本结构JSON',
  `rhythm_structure_json` json DEFAULT NULL COMMENT '节奏结构JSON',
  `packaging_structure_json` json DEFAULT NULL COMMENT '包装结构JSON',
  `llm_token_usage_json` json DEFAULT NULL COMMENT 'LLM Token消耗JSON',
  `provenance_json` json DEFAULT NULL COMMENT '溯源节点JSON',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_result_timeline_biz_id` (`biz_id`),
  UNIQUE KEY `uk_analysis_result_timeline_task_id` (`task_id`),
  KEY `idx_timeline_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解时序大资产表(冷数据层)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `analysis_video_material`
--

DROP TABLE IF EXISTS `analysis_video_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `analysis_video_material` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频文件路径',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '文件大小(字节)',
  `duration` decimal(10,3) DEFAULT NULL COMMENT '视频时长(秒)',
  `width` smallint unsigned DEFAULT NULL COMMENT '视频宽度',
  `height` smallint unsigned DEFAULT NULL COMMENT '视频高度',
  `format` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频格式(mp4/mov/avi/webm)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PROCESSING/DELETED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analysis_video_material_biz_id` (`biz_id`),
  KEY `idx_analysis_video_material_status_created` (`status`,`created_at`),
  CONSTRAINT `chk_analysis_video_material_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'PROCESSING',_utf8mb4'DELETED')))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解流视频素材库(仅视频)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `asr_segment`
--

DROP TABLE IF EXISTS `asr_segment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asr_segment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
  `segment_index` smallint unsigned NOT NULL COMMENT 'ASR片段序号',
  `text` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ASR转写文本',
  `start_time` decimal(10,3) NOT NULL COMMENT '片段起始时间(秒)',
  `end_time` decimal(10,3) NOT NULL COMMENT '片段结束时间(秒)',
  `speaker_label` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说话人标签',
  `confidence` decimal(4,3) DEFAULT NULL COMMENT 'ASR置信度',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asr_segment_biz_id` (`biz_id`),
  UNIQUE KEY `uk_task_asr_seg` (`task_id`,`segment_index`),
  KEY `idx_asr_task` (`task_id`),
  KEY `idx_asr_time` (`task_id`,`start_time`),
  CONSTRAINT `chk_asr_time` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASR转写片段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `creative_material`
--

DROP TABLE IF EXISTS `creative_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creative_material` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `material_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '素材类型: TEXT/IMAGE/VIDEO',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '素材文件路径(文本可为空)',
  `text_content` text COLLATE utf8mb4_unicode_ci COMMENT '文案内容(material_type=TEXT时使用)',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '文件大小(字节)',
  `duration` decimal(10,3) DEFAULT NULL COMMENT '时长(视频)',
  `width` smallint unsigned DEFAULT NULL COMMENT '宽度(图片/视频)',
  `height` smallint unsigned DEFAULT NULL COMMENT '高度(图片/视频)',
  `format` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '格式(jpg/png/webp/mp4等)',
  `tags` json DEFAULT NULL COMMENT '标签(JSON数组)',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '素材描述',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PROCESSING/DELETED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creative_material_biz_id` (`biz_id`),
  KEY `idx_creative_material_type_status_created` (`material_type`,`status`,`created_at`),
  CONSTRAINT `chk_creative_material_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'PROCESSING',_utf8mb4'DELETED'))),
  CONSTRAINT `chk_creative_material_type` CHECK ((`material_type` in (_utf8mb4'TEXT',_utf8mb4'IMAGE',_utf8mb4'VIDEO')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作流素材库(文案/图片/视频)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `deconstruct_project`
--

DROP TABLE IF EXISTS `deconstruct_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deconstruct_project` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `project_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对外项目ID(prj_xxx)',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目标题',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目描述',
  `tags_json` json DEFAULT NULL COMMENT '项目标签(JSON数组)',
  `cover_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面URL(支持http(s)/file/相对路径/绝对路径)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '项目状态: PENDING/PROCESSING/COMPLETED/FAILED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deconstruct_project_biz_id` (`biz_id`),
  UNIQUE KEY `uk_deconstruct_project_project_id` (`project_id`),
  KEY `idx_deconstruct_project_status_updated` (`status`,`updated_at`),
  KEY `idx_deconstruct_project_title` (`title`),
  CONSTRAINT `chk_deconstruct_project_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解项目主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `deconstruct_project_material`
--

DROP TABLE IF EXISTS `deconstruct_project_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deconstruct_project_material` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `project_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '拆解项目ID(deconstruct_project.project_id)',
  `material_biz_id` bigint unsigned NOT NULL COMMENT '素材业务主键(analysis_video_material.biz_id)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上传触发的任务ID(video_analysis_task.task_id)',
  `relation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRIMARY' COMMENT '关联类型: PRIMARY/REFERENCE',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '项目内素材排序(越小越靠前)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deconstruct_project_material_biz_id` (`biz_id`),
  UNIQUE KEY `uk_project_material` (`project_id`,`material_biz_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_material_biz_id` (`material_biz_id`),
  KEY `idx_task_id` (`task_id`),
  CONSTRAINT `chk_relation_type` CHECK ((`relation_type` in (_utf8mb4'PRIMARY',_utf8mb4'REFERENCE')))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拆解项目与素材关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `key_frame`
--

DROP TABLE IF EXISTS `key_frame`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `key_frame` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
  `frame_index` tinyint unsigned NOT NULL COMMENT '关键帧序号(1-5)',
  `time_point` decimal(10,3) NOT NULL COMMENT '抽帧时间点(秒)',
  `source_shot_index` smallint unsigned DEFAULT NULL COMMENT '来源镜头序号',
  `extraction_reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抽帧原因(HOOK_FIRST/HOOK_MID/TOP_SCORE)',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关键帧文件路径',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '关键帧文本描述(用于Prompt)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_frame_biz_id` (`biz_id`),
  UNIQUE KEY `uk_task_frame` (`task_id`,`frame_index`),
  KEY `idx_keyframe_task` (`task_id`),
  CONSTRAINT `chk_extraction_reason` CHECK ((`extraction_reason` in (_utf8mb4'HOOK_FIRST',_utf8mb4'HOOK_MID',_utf8mb4'TOP_SCORE'))),
  CONSTRAINT `chk_frame_index` CHECK (((`frame_index` >= 1) and (`frame_index` <= 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键帧抽取结果';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shot`
--

DROP TABLE IF EXISTS `shot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shot` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联任务ID(video_analysis_task.task_id)',
  `shot_index` smallint unsigned NOT NULL COMMENT '镜头序号(从0开始)',
  `start_time` decimal(10,3) NOT NULL COMMENT '镜头起始时间(秒)',
  `end_time` decimal(10,3) NOT NULL COMMENT '镜头结束时间(秒)',
  `duration` decimal(10,3) NOT NULL COMMENT '镜头时长(秒)',
  `scene_score` decimal(4,3) DEFAULT NULL COMMENT '场景变化分值(0-1)',
  `thumbnail_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '镜头缩略图路径',
  `shot_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '镜头类型(LLM推断)',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '镜头描述(LLM生成)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shot_biz_id` (`biz_id`),
  UNIQUE KEY `uk_task_shot` (`task_id`,`shot_index`),
  KEY `idx_shot_task` (`task_id`),
  KEY `idx_shot_score` (`task_id`,`scene_score` DESC),
  CONSTRAINT `chk_shot_time` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='镜头切分结果';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `video_analysis_task`
--

DROP TABLE IF EXISTS `video_analysis_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video_analysis_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `biz_id` bigint unsigned NOT NULL COMMENT '业务主键(雪花ID)',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID(兼容现有API，UUID)',
  `source_video_biz_id` bigint unsigned NOT NULL COMMENT '来源视频业务主键(analysis_video_material.biz_id)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING/PROCESSING/ANALYZING/COMPLETED/FAILED/CANCELED',
  `progress` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '任务进度百分比(0-100)',
  `progress_step` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前处理步骤描述',
  `source_file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源视频路径快照(便于审计)',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '文件大小(字节)',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '失败错误信息',
  `error_step` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败步骤',
  `retry_count` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '重试次数(最大3)',
  `priority` tinyint unsigned NOT NULL DEFAULT '5' COMMENT '任务优先级(1最高,10最低)',
  `redis_progress_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Redis进度缓存Key',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '完成时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_analysis_task_biz_id` (`biz_id`),
  UNIQUE KEY `uk_video_analysis_task_task_id` (`task_id`),
  KEY `idx_task_status_priority` (`status`,`priority`),
  KEY `idx_task_created_at` (`created_at`),
  KEY `idx_task_status_updated` (`status`,`updated_at`),
  KEY `idx_task_source_video_biz_id` (`source_video_biz_id`),
  CONSTRAINT `chk_task_priority` CHECK (((`priority` >= 1) and (`priority` <= 10))),
  CONSTRAINT `chk_task_progress` CHECK (((`progress` >= 0) and (`progress` <= 100))),
  CONSTRAINT `chk_task_retry` CHECK ((`retry_count` <= 3)),
  CONSTRAINT `chk_task_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'ANALYZING',_utf8mb4'COMPLETED',_utf8mb4'FAILED',_utf8mb4'CANCELED')))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频拆解任务主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'redstve'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-23 17:24:19
