package com.agentscope.codewiki.dify;

import java.util.Locale;

/** Supported Dify chat response modes. */
public enum DifyResponseMode {
    BLOCKING("blocking"),
    STREAMING("streaming");

    private final String wireValue;

    DifyResponseMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static DifyResponseMode from(String value) {
        if (value == null || value.isBlank()) {
            return BLOCKING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DifyResponseMode mode : values()) {
            if (mode.wireValue.equals(normalized)
                    || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown Dify response mode: " + value + " (expected blocking or streaming)");
    }
}
