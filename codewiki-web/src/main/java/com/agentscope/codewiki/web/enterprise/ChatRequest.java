package com.agentscope.codewiki.web.enterprise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** REST request for the enterprise single-agent chat API. */
public record ChatRequest(
        @NotBlank @Size(max = 128) String userId,
        @NotBlank @Size(max = 128) String sessionId,
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 10000) String message) {}
