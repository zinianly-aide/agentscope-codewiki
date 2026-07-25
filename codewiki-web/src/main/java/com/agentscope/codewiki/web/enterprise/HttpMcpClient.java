package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpMcpClient implements McpClient {
    private static final Logger log = LoggerFactory.getLogger(HttpMcpClient.class);
    private final McpServerProperties properties;
    private final ObjectMapper objectMapper;
    private volatile McpToolsResponse cached = new McpToolsResponse("codewiki-demo-mcp", List.of());
    private volatile String status = "unknown";
    private volatile boolean available;

    public HttpMcpClient(McpServerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public McpToolsResponse discoverTools() {
        if (!properties.isEnabled()) {
            status = "disabled";
            available = false;
            cached = new McpToolsResponse(properties.demo().getName(), List.of());
            return cached;
        }
        try {
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(properties.demo().getConnectTimeout())
                            .build();
            HttpRequest request =
                    HttpRequest.newBuilder(toUri("/mcp/tools"))
                            .timeout(properties.demo().getReadTimeout())
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                cached = objectMapper.readValue(response.body(), McpToolsResponse.class);
                available = true;
                status = "available@" + Instant.now();
            } else {
                available = false;
                status = "unavailable:http-" + response.statusCode();
            }
        } catch (Exception ex) {
            available = false;
            status = "unavailable:" + ex.getClass().getSimpleName();
            log.warn("MCP demo unavailable: {}", ex.getClass().getSimpleName());
            cached = new McpToolsResponse(properties.demo().getName(), List.of());
        }
        return cached;
    }

    @Override
    public McpToolCallResponse<?> call(String toolName, Map<String, Object> arguments) {
        if (cached.tools().stream().noneMatch(tool -> tool.name().equals(toolName))) {
            return new McpToolCallResponse<>(
                    false,
                    toolName,
                    null,
                    "mcp:" + properties.demo().getName(),
                    "TOOL_NOT_REGISTERED",
                    "MCP tool is not registered");
        }
        try {
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(properties.demo().getConnectTimeout())
                            .build();
            String body = objectMapper.writeValueAsString(new McpToolCallRequest(arguments));
            HttpRequest request =
                    HttpRequest.newBuilder(toUri("/mcp/tools/" + toolName + "/call"))
                            .timeout(properties.demo().getReadTimeout())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return new McpToolCallResponse<>(
                        false,
                        toolName,
                        null,
                        "mcp:" + properties.demo().getName(),
                        "TOOL_NOT_FOUND",
                        "MCP tool not found");
            }
            if (response.statusCode() >= 400) {
                return new McpToolCallResponse<>(
                        false,
                        toolName,
                        null,
                        "mcp:" + properties.demo().getName(),
                        "MCP_HTTP_ERROR",
                        "MCP server returned " + response.statusCode());
            }
            available = true;
            return objectMapper.readValue(response.body(), McpToolCallResponse.class);
        } catch (Exception ex) {
            available = false;
            status = "unavailable:" + ex.getClass().getSimpleName();
            return new McpToolCallResponse<>(
                    false,
                    toolName,
                    null,
                    "mcp:" + properties.demo().getName(),
                    "MCP_UNAVAILABLE",
                    "MCP server unavailable: " + ex.getClass().getSimpleName());
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String status() {
        return status;
    }

    private URI toUri(String path) {
        return URI.create(properties.demo().getBaseUrl().replaceAll("/$", "") + path);
    }
}
