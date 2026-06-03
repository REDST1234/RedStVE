package com.bytedance.aivideo.creation.dto.remotion;

import lombok.Data;
import java.util.List;

@Data
public class CompositionScript {
    private String $schema;
    private String projectId;
    private CanvasConfig canvas;
    private GlobalStyle globalStyle;
    private BgmConfig bgm;
    private List<Scene> scenes;
    private List<Transition> transitions;
}
