package com.bytedance.aivideo.creation.dto.profile;

import lombok.Data;

@Data
public class BoundingBoxDto {
    private Integer x;
    private Integer y;
    private Integer w;
    private Integer h;
}
