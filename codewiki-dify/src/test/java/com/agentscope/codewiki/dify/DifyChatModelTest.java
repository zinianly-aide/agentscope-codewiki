package com.agentscope.codewiki.dify;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration test for {@link DifyChatModel} using a local mock HTTP server. */
class DifyChatModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private DifyChatModel model;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        DifyClient client =
                new DifyClient(
                        "http://localhost:" + server.getAddress().getPort(),
                        "app-key",
                        HttpClient.newHttpClient());

        model = new DifyChatModel(client, "user-42", null);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldChatAndReturnAnswer() throws Exception {
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String body =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event", "message",
                                            "message_id", "m1",
                                            "conversation_id", "conv-new",
                                            "answer", "The answer is 42.",
                                            "created_at", 1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        String answer = model.chat("What is the answer?");
        assertEquals("The answer is 42.", answer);
    }

    @Test
    void shouldTrackConversationId() throws Exception {
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    String cid = (String) reqMap.get("conversation_id");

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event", "message",
                                            "message_id", cid != null ? "m2" : "m1",
                                            "conversation_id", "conv-tracked",
                                            "answer", "tracked",
                                            "created_at", 1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        // First message — no conversation ID yet
        assertNull(model.getConversationId());
        model.chat("first");
        assertEquals("conv-tracked", model.getConversationId());

        // Second message — conversation ID should be sent
        model.chat("second");
        assertEquals("conv-tracked", model.getConversationId());
    }

    @Test
    void shouldResumeExistingConversation() throws Exception {
        model.setConversationId("resume-123");

        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    assertEquals(
                            "resume-123",
                            reqMap.get("conversation_id"),
                            "Should pass existing conversation ID");

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event", "message",
                                            "message_id", "m-resume",
                                            "conversation_id", "resume-123",
                                            "answer", "continuing",
                                            "created_at", 1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        model.chat("continue");
        assertEquals("resume-123", model.getConversationId());
    }

    @Test
    void shouldPassInputVariables() throws Exception {
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputs = (Map<String, Object>) reqMap.get("inputs");
                    assertEquals("src/main/java", inputs.get("code_path"));
                    assertEquals("Java", inputs.get("language"));

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event",
                                            "message",
                                            "message_id",
                                            "m",
                                            "conversation_id",
                                            "c",
                                            "answer",
                                            "with inputs",
                                            "created_at",
                                            1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        DifyChatResponse response =
                model.chat(
                        "analyze",
                        Map.of(
                                "code_path", "src/main/java",
                                "language", "Java"));

        assertEquals("with inputs", response.getAnswer());
    }
}
