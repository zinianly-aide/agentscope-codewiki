package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductionEnhancementTest {
    @Test
    void retryMetricsAndAuditAreRecorded() {
        AgentProgressCapture capture = new AgentProgressCapture();
        AgentToolRegistry registry =
                new AgentToolRegistry(
                        new ToolRetryExecutor(2, 1),
                        new ToolMetricsRegistry(),
                        new ToolAuditService());
        registry.register(new FlakyTool());

        ToolResult<?> result =
                registry.execute(
                        "flaky",
                        Map.of(),
                        new ToolExecutionContext("req-retry", "conv-retry", capture));

        assertTrue(result.success());
        assertTrue(capture.types.contains(AgentEventType.TOOL_RETRY));
        assertTrue(registry.metricsSnapshot().containsKey("flaky"));
        ToolAuditRecord audit = registry.recentAudits(1).getFirst();
        assertEquals("req-retry", audit.requestId());
        assertEquals("conv-retry", audit.conversationId());
        assertEquals("SUCCESS", audit.status());
    }

    @Test
    void conversationExportAndCleanupRemainInMemory() {
        ConversationProperties props = new ConversationProperties();
        props.setExpireAfterAccess(java.time.Duration.ofMinutes(30));
        InMemoryConversationStore store = new InMemoryConversationStore(props);
        ConversationExecution execution = store.create("req-export", "question text");
        execution.answer("answer text");
        execution.append(
                AgentEventType.REQUEST_ACCEPTED,
                "accepted",
                "ok",
                AgentEventStatus.COMPLETED,
                1,
                Map.of());
        ConversationExportResponse export =
                new ConversationExportResponse(
                        execution.conversationId(),
                        execution.requestId(),
                        execution.question(),
                        execution.answer(),
                        execution.snapshotEvents());
        assertEquals("question text", export.question());
        assertEquals(1, export.events().size());
        store.cleanupExpired();
        assertTrue(store.find(execution.conversationId()).isPresent());
    }

    @Test
    void skillHotReloadSeesChangedSkillFiles() throws Exception {
        Path root = Files.createTempDirectory("skills");
        Path skill = Files.createDirectories(root.resolve("demo"));
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: demo\ndescription: one\n---\n");
        SkillHotReloadService service = new SkillHotReloadService(new SkillRepository(root, 65536));
        assertEquals(1, service.latestSkills().size());
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: demo\ndescription: two\n---\n");
        service.reloadSkills();
        assertEquals(1, service.latestSkills().size());
        assertTrue(service.latestSkills().getFirst().description().contains("two"));
    }

    @Test
    void mcpRefreshReplacesMcpTools() {
        AgentToolRegistry registry = new AgentToolRegistry();
        FakeMcpClient client = new FakeMcpClient();
        registry.refreshMcpTools(client.discoverTools().tools(), client);
        assertTrue(registry.find("remote_tool").isPresent());
        ToolResult<?> result =
                registry.execute("remote_tool", Map.of(), new ToolExecutionContext("req"));
        assertTrue(result.success());
    }

    @Test
    void llmReactUsesOpenAiCompatibleToolCalls() {
        EnterpriseAgentProperties props = llmProps("http://stub/v1");
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.register(new EchoTool());
        StubModelClient model = new StubModelClient();
        LlmReActAgent agent =
                new LlmReActAgent(
                        props,
                        model,
                        registry,
                        new SkillRepository(Path.of("missing"), 65536),
                        (userId, tool) -> true,
                        new ToolCallValidator(new ObjectMapper()),
                        new ObjectMapper());

        AgentChatResult result =
                agent.chat(List.of(), "call echo", new ToolExecutionContext("req-llm"), "E10086");

        assertEquals(ExecutionMode.LLM_REACT, result.executionMode());
        assertTrue(result.answer().contains("final answer"));
        assertEquals("auto", model.firstRequest.tool_choice());
        assertFalse(model.firstRequest.tools().isEmpty());
        assertFalse(model.firstRequest.messages().isEmpty());
    }

    @Test
    void openAiCompatibleClientParsesHttpToolCallsAndSendsTools() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        StringBuilder captured = new StringBuilder();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    captured.append(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    calls.incrementAndGet();
                    byte[] response =
                            ("""
                            {
                              "model": "stub-model",
                              "choices": [{
                                "message": {
                                  "role": "assistant",
                                  "content": null,
                                  "tool_calls": [{
                                    "id": "call-remote-1",
                                    "type": "function",
                                    "function": {
                                      "name": "echo",
                                      "arguments": "{\\\"text\\\":\\\"hello\\\"}"
                                    }
                                  }]
                                }
                              }],
                              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                            }
                            """)
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            EnterpriseAgentProperties props =
                    llmProps("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            OpenAiCompatibleAgentModelClient client =
                    new OpenAiCompatibleAgentModelClient(props, mapper);
            ModelChatResponse response =
                    client.chat(
                            new ModelChatRequest(
                                    "stub-model",
                                    List.of(ModelMessage.user("call echo")),
                                    List.of(ModelToolDefinition.fromTool(new EchoTool())),
                                    "auto",
                                    0D));

            assertEquals(1, calls.get());
            assertEquals(1, response.toolCalls().size());
            assertEquals("echo", response.toolCalls().getFirst().function().name());
            assertTrue(response.toolCalls().getFirst().function().arguments().contains("hello"));
            JsonNode request = mapper.readTree(captured.toString());
            assertEquals("stub-model", request.path("model").asText());
            assertEquals("auto", request.path("tool_choice").asText());
            assertEquals(
                    "echo", request.path("tools").path(0).path("function").path("name").asText());
        } finally {
            server.stop(0);
        }
    }

    private static EnterpriseAgentProperties llmProps(String baseUrl) {
        EnterpriseAgentProperties props = new EnterpriseAgentProperties();
        props.setExecutionMode("llm-react");
        props.setModel("stub-model");
        props.setApiKey("stub-key");
        props.setBaseUrl(baseUrl);
        return props;
    }

    static class AgentProgressCapture implements AgentProgressPublisher {
        final java.util.List<AgentEventType> types = new java.util.ArrayList<>();

        @Override
        public void publish(
                AgentEventType type,
                String stage,
                String message,
                AgentEventStatus status,
                int progress,
                Map<String, Object> metadata) {
            types.add(type);
        }
    }

    static class FlakyTool implements AgentTool {
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String name() {
            return "flaky";
        }

        @Override
        public String description() {
            return "flaky";
        }

        @Override
        public ToolSource source() {
            return ToolSource.MCP;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context) {
            if (attempts.incrementAndGet() == 1) {
                return ToolResult.failure(
                        name(), "mcp:test", 1, "MCP_UNAVAILABLE", "ConnectException");
            }
            return ToolResult.success(name(), Map.of("ok", true), "mcp:test", 2);
        }
    }

    static class EchoTool implements AgentTool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo";
        }

        @Override
        public ToolSource source() {
            return ToolSource.LOCAL_JAVA;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string")));
        }

        @Override
        public ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context) {
            return ToolResult.success(name(), arguments, "local:test", 1);
        }
    }

    static class FakeMcpClient implements McpClient {
        @Override
        public McpToolsResponse discoverTools() {
            return new McpToolsResponse(
                    "fake",
                    List.of(
                            new McpToolDescriptor(
                                    "remote_tool", "remote", Map.of("type", "object"))));
        }

        @Override
        public McpToolCallResponse<?> call(String toolName, Map<String, Object> arguments) {
            return new McpToolCallResponse<>(
                    true, toolName, Map.of("ok", true), "mcp:fake", null, null);
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String status() {
            return "available";
        }
    }

    static class StubModelClient implements AgentModelClient {
        ModelChatRequest firstRequest;
        int calls;

        @Override
        public ModelChatResponse chat(ModelChatRequest request) {
            calls++;
            if (firstRequest == null) {
                firstRequest = request;
            }
            if (calls == 1) {
                return new ModelChatResponse(
                        "",
                        List.of(
                                new ModelToolCall(
                                        "call-1",
                                        "function",
                                        new ModelFunctionCall("echo", "{\"text\":\"hello\"}"))),
                        null,
                        "stub-model");
            }
            return new ModelChatResponse("final answer", List.of(), null, "stub-model");
        }
    }
}
