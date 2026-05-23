package com.bytedance.aivideo.deconstruct.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 拆解项目分页响应体。
 */
@Data
public class DeconstructProjectListResponse {

    private long total;

    private int page;

    private int size;

    private List<DeconstructProjectResponse> list = new ArrayList<>();
}
