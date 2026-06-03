package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;
import java.util.List;

@Data
public class Scene {
    private String sceneId;
    private Integer sceneIndex;
    private String role;
    private Integer durationInFrames;
    private List<Layer> layers;
}
