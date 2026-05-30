package com.bytedance.aivideo.creation.service;

import com.bytedance.aivideo.creation.dto.profile.PhysicalAttributesDto;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface MultimodalLlmService {
    
    String analyzeProfile(
            String materialType,
            List<Path> imagePaths,
            Optional<String> asrText,
            String textContent,
            PhysicalAttributesDto physicalAttributes
    );
}
