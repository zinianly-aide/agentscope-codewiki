package com.agentscope.codewiki.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Stateless MCP Streamable HTTP JSON-RPC endpoint, alongside the legacy demo bridge. */
@RestController
public class StandardMcpController {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private final DemoMcpToolRegistry registry;
    private final ObjectMapper mapper;

    public StandardMcpController(DemoMcpToolRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @PostMapping(path = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handle(@RequestBody JsonNode request) {
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())) {
            return json(error(request.path("id"), -32600, "invalid JSON-RPC request"));
        }
        String method = request.path("method").asText("");
        if ("notifications/initialized".equals(method) && !request.has("id")) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        if (!request.has("id") || method.isBlank()) {
            return json(error(request.path("id"), -32600, "invalid JSON-RPC request"));
        }
        JsonNode id = request.get("id");
        JsonNode params = request.path("params");
        try {
            Object result =
                    switch (method) {
                        case "initialize" ->
                                Map.of(
                                        "protocolVersion", PROTOCOL_VERSION,
                                        "capabilities",
                                                Map.of("tools", Map.of("listChanged", false)),
                                        "serverInfo",
                                                Map.of("name", "codewiki", "version", "0.0.1"));
                        case "ping" -> Map.of();
                        case "tools/list" ->
                                Map.of(
                                        "tools",
                                        registry.descriptors().stream()
                                                .map(
                                                        d ->
                                                                Map.of(
                                                                        "name",
                                                                        d.name(),
                                                                        "description",
                                                                        d.description(),
                                                                        "inputSchema",
                                                                        d.inputSchema()))
                                                .toList());
                        case "tools/call" -> callTool(params);
                        default -> null;
                    };
            if (result == null) {
                return json(error(id, -32601, "method not found"));
            }
            return json(Map.of("jsonrpc", "2.0", "id", id, "result", result));
        } catch (IllegalArgumentException ex) {
            return json(error(id, -32602, "invalid params"));
        }
    }

    private Map<String, Object> callTool(JsonNode params) {
        String name = params.path("name").asText("");
        if (name.isBlank()) {
            throw new IllegalArgumentException("missing tool name");
        }
        if (!registry.registered(name)) {
            return content(true, Map.of("errorCode", "TOOL_NOT_FOUND", "message", "unknown tool"));
        }
        JsonNode arguments = params.path("arguments");
        if (!arguments.isMissingNode() && !arguments.isObject()) {
            throw new IllegalArgumentException("arguments must be an object");
        }
        Map<String, Object> input =
                arguments.isMissingNode()
                        ? Map.of()
                        : mapper.convertValue(
                                arguments,
                                new com.fasterxml.jackson.core.type.TypeReference<>() {});
        McpToolCallResponse<?> response = registry.call(name, input);
        return content(
                !response.success(),
                response.success()
                        ? response.data()
                        : Map.of(
                                "errorCode",
                                response.errorCode(),
                                "message",
                                response.errorMessage()));
    }

    private Map<String, Object> content(boolean isError, Object data) {
        String encoded;
        try {
            encoded = mapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            encoded = "{}";
        }
        return Map.of(
                "isError", isError, "content", List.of(Map.of("type", "text", "text", encoded)));
    }

    private Map<String, Object> error(JsonNode id, int code, String message) {
        return Map.of(
                "jsonrpc",
                "2.0",
                "id",
                id.isMissingNode() ? mapper.nullNode() : id,
                "error",
                Map.of("code", code, "message", message));
    }

    private ResponseEntity<?> json(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
