package com.agentscope.codewiki.mcp;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/mcp")
public class McpController {
    private final DemoMcpToolRegistry registry;

    public McpController(DemoMcpToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/tools")
    public McpToolsResponse tools() {
        return new McpToolsResponse("codewiki-demo-mcp", registry.descriptors());
    }

    @PostMapping("/tools/{toolName}/call")
    public ResponseEntity<McpToolCallResponse<?>> call(
            @PathVariable String toolName, @Valid @RequestBody McpToolCallRequest request) {
        if (!registry.registered(toolName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                            McpToolCallResponse.failure(
                                    toolName, "TOOL_NOT_FOUND", "unknown MCP tool"));
        }
        McpToolCallResponse<?> response = registry.call(toolName, request.arguments());
        return ResponseEntity.ok(response);
    }

    @RestControllerAdvice(assignableTypes = McpController.class)
    static class McpErrorHandler {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<Map<String, Object>> validation() {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "errorCode",
                                    "VALIDATION_ERROR",
                                    "message",
                                    "invalid MCP request"));
        }
    }
}
