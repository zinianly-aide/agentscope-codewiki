package com.agentscope.codewiki.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class McpAnalysisIntegrationTest {
    @TempDir static Path root;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) {
        registry.add("codewiki.analysis.enabled", () -> "true");
        registry.add("codewiki.analysis.repository-root", root::toString);
    }

    @BeforeAll
    static void fixture() throws Exception {
        Files.writeString(root.resolve("A.java"), "class A { void exampleMethod() {} }");
    }

    @Test
    void discoversAndCallsRepositoryAnalysisThroughExistingHttpBridge() throws Exception {
        mvc.perform(get("/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("codewiki_search_symbol")))
                .andExpect(content().string(containsString("codewiki_search_code")))
                .andExpect(content().string(containsString("codewiki_analyze_impact")));
        mvc.perform(
                        post("/mcp/tools/codewiki_search_symbol/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"arguments\":{\"query\":\"exampleMethod\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(content().string(containsString("A.java")));
        mvc.perform(
                        post("/mcp/tools/codewiki_analyze_impact/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"arguments\":{\"file\":\"../secret\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENTS"));
    }
}
