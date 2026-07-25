package com.agentscope.codewiki.web.enterprise;

import java.util.Locale;

/** Supported single-agent execution modes. */
public enum ExecutionMode {
    DETERMINISTIC("deterministic"),
    LLM_REACT("llm-react");

    private final String wireValue;

    ExecutionMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ExecutionMode from(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        if (LLM_REACT.wireValue.equals(normalized)) {
            return LLM_REACT;
        }
        return DETERMINISTIC;
    }
}
