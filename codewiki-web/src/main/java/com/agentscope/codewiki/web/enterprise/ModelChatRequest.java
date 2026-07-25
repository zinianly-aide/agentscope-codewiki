package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelChatRequest(
        String model,
        List<ModelMessage> messages,
        List<ModelToolDefinition> tools,
        String tool_choice,
        Double temperature) {}
