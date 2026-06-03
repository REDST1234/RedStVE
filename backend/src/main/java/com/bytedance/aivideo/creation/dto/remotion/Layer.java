package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;
import java.util.Map;

@Data
public class Layer {
    private String layerId;
    private String preset;
    private Integer enterAtFrame;
    private Integer durationInFrames;
    private Map<String, Object> params;
}
