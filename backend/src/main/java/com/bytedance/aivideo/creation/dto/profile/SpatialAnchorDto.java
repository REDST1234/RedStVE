package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

@Data
public class SpatialAnchorDto {
    private String subject;
    private BoundingBoxDto boundingBox;
}
