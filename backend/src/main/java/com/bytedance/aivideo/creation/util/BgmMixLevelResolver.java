package com.bytedance.aivideo.creation.util;

import java.util.Locale;

public final class BgmMixLevelResolver {

    private BgmMixLevelResolver() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "BALANCED";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "QUIET", "BALANCED", "DRIVE" -> normalized;
            default -> "BALANCED";
        };
    }

    public static double resolveVolume(String mixLevel) {
        String normalized = normalize(mixLevel);
        return switch (normalized) {
            case "QUIET" -> 0.18D;
            case "DRIVE" -> 0.38D;
            default -> 0.28D;
        };
    }
}
