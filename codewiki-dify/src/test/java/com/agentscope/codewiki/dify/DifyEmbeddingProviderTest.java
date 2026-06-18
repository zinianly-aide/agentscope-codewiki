package com.agentscope.codewiki.dify;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link DifyEmbeddingProvider} using a local mock HTTP server. */
class DifyEmbeddingProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private DifyEmbeddingProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        provider =
                new DifyEmbeddingProvider(
                        "http://localhost:" + server.getAddress().getPort(),
                        "embed-key",
                        HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSendOpenAiCompatibleEmbeddingRequestAndParseResponse() throws Exception {
        server.createContext(
                "/v1/embeddings",
                exchange -> {
                    assertEquals("POST", exchange.getRequestMethod());
                    assertEquals(
                            "Bearer embed-key",
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    assertEquals(
                            "application/json",
                            exchange.getRequestHeaders().getFirst("Content-Type"));

                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    assertEquals("text-embedding-3-small", reqMap.get("model"));
                    assertEquals(List.of("hello world"), reqMap.get("input"));

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "object",
                                            "list",
                                            "model",
                                            "text-embedding-3-small",
                                            "data",
                                            List.of(
                                                    Map.of(
                                                            "object",
                                                            "embedding",
                                                            "index",
                                                            0,
                                                            "embedding",
                                                            List.of(0.1, 0.2, 0.3))),
                                            "usage",
                                            Map.of(
                                                    "prompt_tokens", 2,
                                                    "total_tokens", 2)));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        EmbeddingResponse response =
                provider.embed(EmbeddingRequest.of("text-embedding-3-small", "hello world"));

        assertEquals("list", response.getObject());
        assertEquals("text-embedding-3-small", response.getModel());
        assertEquals(1, response.getData().size());
        assertEquals(0, response.getData().get(0).getIndex());
        assertEquals(List.of(0.1, 0.2, 0.3), response.firstEmbedding());
        assertEquals(2, response.getUsage().getPromptTokens());
        assertEquals(2, response.getUsage().getTotalTokens());
    }

    @Test
    void shouldSupportBatchInput() throws Exception {
        server.createContext(
                "/v1/embeddings",
                exchange -> {
                    String reqBody =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqMap = MAPPER.readValue(reqBody, Map.class);
                    assertEquals(List.of("a", "b"), reqMap.get("input"));

                    String respBody =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "object",
                                            "list",
                                            "model",
                                            "embed-model",
                                            "data",
                                            List.of(
                                                    Map.of(
                                                            "object",
                                                            "embedding",
                                                            "index",
                                                            0,
                                                            "embedding",
                                                            List.of(1.0, 0.0)),
                                                    Map.of(
                                                            "object",
                                                            "embedding",
                                                            "index",
                                                            1,
                                                            "embedding",
                                                            List.of(0.0, 1.0))),
                                            "usage",
                                            Map.of("prompt_tokens", 2, "total_tokens", 2)));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            200, respBody.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBody.getBytes(StandardCharsets.UTF_8));
                    }
                });

        EmbeddingResponse response =
                provider.embed(EmbeddingRequest.of("embed-model", List.of("a", "b")));

        assertEquals(2, response.getData().size());
        assertEquals(List.of(1.0, 0.0), response.getData().get(0).getEmbedding());
        assertEquals(List.of(0.0, 1.0), response.getData().get(1).getEmbedding());
    }

    @Test
    void shouldThrowDifyExceptionOnNon200() {
        server.createContext(
                "/v1/embeddings",
                exchange -> {
                    String body = "{\"error\":\"invalid_api_key\"}";
                    exchange.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        DifyException ex =
                assertThrows(
                        DifyException.class,
                        () -> provider.embed(EmbeddingRequest.of("embed-model", "hello")));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void shouldNormalizeTrailingSlashBaseUrl() throws Exception {
        DifyEmbeddingProvider slashProvider =
                new DifyEmbeddingProvider(
                        "http://localhost:" + server.getAddress().getPort() + "/",
                        "key",
                        HttpClient.newHttpClient());

        server.createContext(
                "/v1/embeddings",
                exchange -> {
                    String body =
                            MAPPER.writeValueAsString(
                                    Map.of(
                                            "object",
                                            "list",
                                            "model",
                                            "m",
                                            "data",
                                            List.of(
                                                    Map.of(
                                                            "object",
                                                            "embedding",
                                                            "index",
                                                            0,
                                                            "embedding",
                                                            List.of(0.5))),
                                            "usage",
                                            Map.of("prompt_tokens", 1, "total_tokens", 1)));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        EmbeddingResponse response = slashProvider.embed(EmbeddingRequest.of("m", "x"));
        assertEquals(List.of(0.5), response.firstEmbedding());
        assertFalse(slashProvider.getBaseUrl().endsWith("/"));
    }

    @Test
    void shouldValidateRequest() {
        assertThrows(IllegalArgumentException.class, () -> provider.embed(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.embed(new EmbeddingRequest(null, "x")));
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.embed(new EmbeddingRequest("model", List.of())));
    }
}
