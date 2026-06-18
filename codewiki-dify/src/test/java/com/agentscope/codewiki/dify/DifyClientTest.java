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

/** Integration test for {@link DifyClient} using a local mock HTTP server. */
class DifyClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private int port;
    private DifyClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(null); // default executor
        server.start();

        client =
                new DifyClient(
                        "http://localhost:" + port, "app-test-key-123", HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSendChatRequestAndParseResponse() throws Exception {
        // given: a mock Dify endpoint
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    // Verify request method and headers
                    assertEquals("POST", exchange.getRequestMethod());
                    assertEquals(
                            "Bearer app-test-key-123",
                            exchange.getRequestHeaders().getFirst("Authorization"));

                    // Parse and verify request body
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    assertEquals("Hello", reqMap.get("query"));
                    assertEquals("user-1", reqMap.get("user"));
                    assertEquals("blocking", reqMap.get("response_mode"));

                    // Return a valid Dify response
                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event", "message",
                                            "message_id", "msg-001",
                                            "conversation_id", "conv-001",
                                            "answer", "Hello, how can I help?",
                                            "created_at", 1700000000));

                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        // when
        DifyChatRequest request = new DifyChatRequest("Hello", "user-1");
        DifyChatResponse response = client.chat(request);

        // then
        assertEquals("message", response.getEvent());
        assertEquals("msg-001", response.getMessageId());
        assertEquals("conv-001", response.getConversationId());
        assertEquals("Hello, how can I help?", response.getAnswer());
        assertEquals(1700000000L, response.getCreatedAt());
    }

    @Test
    void shouldThrowDifyExceptionOnNon200() {
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String body = "{\"error\":\"invalid_api_key\"}";
                    exchange.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        DifyChatRequest request = new DifyChatRequest("hi", "user-1");
        DifyException ex = assertThrows(DifyException.class, () -> client.chat(request));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void shouldIncludeConversationIdWhenSet() throws Exception {
        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    assertEquals("conv-existing", reqMap.get("conversation_id"));

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event",
                                            "message",
                                            "message_id",
                                            "m2",
                                            "conversation_id",
                                            "conv-existing",
                                            "answer",
                                            "OK",
                                            "created_at",
                                            1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        DifyChatRequest request = new DifyChatRequest("hi", "user-1");
        request.setConversationId("conv-existing");
        DifyChatResponse response = client.chat(request);

        assertEquals("conv-existing", response.getConversationId());
    }

    @Test
    void shouldTrailingSlashInBaseUrl() throws Exception {
        // Given a client with trailing slash
        DifyClient slashClient =
                new DifyClient("http://localhost:" + port + "/", "key", HttpClient.newHttpClient());

        server.createContext(
                "/v1/chat-messages",
                exchange -> {
                    String body =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "event",
                                            "message",
                                            "message_id",
                                            "m",
                                            "conversation_id",
                                            "c",
                                            "answer",
                                            "yes",
                                            "created_at",
                                            1));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        DifyChatResponse response = slashClient.chat(new DifyChatRequest("q", "u"));
        assertEquals("yes", response.getAnswer());
    }
}
