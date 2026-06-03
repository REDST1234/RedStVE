package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;
import java.util.Map;

@Data
public class TransitionOverlay {
    private String preset;
    private Map<String, Object> params;
}
