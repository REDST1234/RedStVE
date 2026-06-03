package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;
import java.util.Map;

@Data
public class Transition {
    private Integer fromSceneIndex;
    private Integer toSceneIndex;
    private String preset;
    private Map<String, Object> params;
    private TransitionOverlay overlay;
}
