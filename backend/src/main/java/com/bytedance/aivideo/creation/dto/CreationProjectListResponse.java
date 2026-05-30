package com.bytedance.aivideo.creation.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 创作项目分页列表响应。
 */
@Data
public class CreationProjectListResponse {

    private long total;
    private long page;
    private long size;
    private List<CreationProjectListItemResponse> list = new ArrayList<>();
}
