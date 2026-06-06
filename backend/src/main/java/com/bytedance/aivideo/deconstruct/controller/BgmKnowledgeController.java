package com.bytedance.aivideo.deconstruct.controller;

import com.bytedance.aivideo.common.api.ApiResponse;
import com.bytedance.aivideo.common.error.ErrorCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.bytedance.aivideo.deconstruct.service.BgmKnowledgeService;
import com.bytedance.aivideo.infrastructure.vector.BgmVectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/bgm")
public class BgmKnowledgeController {

    private final BgmKnowledgeService bgmKnowledgeService;
    private final BgmVectorProperties bgmVectorProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BgmKnowledgeController(BgmKnowledgeService bgmKnowledgeService,
                                  BgmVectorProperties bgmVectorProperties) {
        this.bgmKnowledgeService = bgmKnowledgeService;
        this.bgmVectorProperties = bgmVectorProperties;
    }

    @PostMapping("/analyze")
    public ApiResponse<Object> analyzeBgm(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_REQUEST, "上传的文件为空");
        }
        
        try {
            Object data = bgmKnowledgeService.processBgmUpload(file);
            return ApiResponse.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "BGM分析失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{audioId}")
    public ApiResponse<Void> deleteBgm(@PathVariable("audioId") String audioId) {
        try {
            bgmKnowledgeService.deleteBgm(audioId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<Object>> listBgm() {
        List<Object> bgmList = new ArrayList<>();
        
        File dbDir = new File(bgmVectorProperties.getAudioDatabaseDir());
        if (!dbDir.exists()) {
            dbDir = new File("../" + bgmVectorProperties.getAudioDatabaseDir());
        }
        
        if (dbDir.exists() && dbDir.isDirectory()) {
            File[] subDirs = dbDir.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File dir : subDirs) {
                    File jsonFile = new File(dir, "audio_data.json");
                    if (jsonFile.exists()) {
                        try {
                            java.util.Map<String, Object> data = objectMapper.readValue(jsonFile, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                            // 强制统一 audioId 为文件夹名称，保证后续播放/删除的路径能正确对应
                            data.put("audioId", dir.getName());
                            bgmList.add(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return ApiResponse.success(bgmList);
    }

    @GetMapping("/play/{audioId}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> playBgm(@PathVariable("audioId") String audioId) {
        try {
            File baseDir = new File(bgmVectorProperties.getAudioDatabaseDir());
            if (!baseDir.exists()) {
                baseDir = new File("../" + bgmVectorProperties.getAudioDatabaseDir());
            }
            File dir = new File(baseDir, audioId);
            if (!dir.exists() || !dir.isDirectory()) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            File[] files = dir.listFiles((d, name) -> !name.equals("audio_data.json") && !name.startsWith("."));
            if (files == null || files.length == 0) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            File audioFile = files[0];
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(audioFile.toURI());
            
            String fileName = audioFile.getName().toLowerCase();
            String contentType = "audio/wav";
            if (fileName.endsWith(".mp3")) contentType = "audio/mpeg";
            else if (fileName.endsWith(".m4a") || fileName.endsWith(".aac") || fileName.endsWith(".mp4")) contentType = "audio/mp4";
            else if (fileName.endsWith(".ogg")) contentType = "audio/ogg";
            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
