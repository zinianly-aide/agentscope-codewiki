package com.agentscope.codewiki.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class McpControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void listsRegisteredTools() throws Exception {
        mockMvc.perform(get("/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverName").value("codewiki-demo-mcp"))
                .andExpect(content().string(containsString("get_platform_owner")))
                .andExpect(content().string(containsString("create_sms_alert_summary")));
    }

    @Test
    void returnsPlatformOwnerAndUnknownPlatformBusinessError() throws Exception {
        mockMvc.perform(
                        post("/mcp/tools/get_platform_owner/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"arguments\":{\"platform\":\"platform-b\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ownerName").value("张三"))
                .andExpect(jsonPath("$.data.ownerEmail").value("platform-b-owner@example.local"));

        mockMvc.perform(
                        post("/mcp/tools/get_platform_owner/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"arguments\":{\"platform\":\"platform-x\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NOT_FOUND"));
    }

    @Test
    void calculatesSeverityAndRejectsUnknownToolOrInvalidRequest() throws Exception {
        mockMvc.perform(
                        post("/mcp/tools/create_sms_alert_summary/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"arguments\":{\"platform\":\"platform-a\",\"blockedRate\":0.9,\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-07\"}}"))
                .andExpect(jsonPath("$.data.severity").value("LOW"));
        mockMvc.perform(
                        post("/mcp/tools/create_sms_alert_summary/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"arguments\":{\"platform\":\"platform-a\",\"blockedRate\":2.0,\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-07\"}}"))
                .andExpect(jsonPath("$.data.severity").value("MEDIUM"));
        mockMvc.perform(
                        post("/mcp/tools/create_sms_alert_summary/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"arguments\":{\"platform\":\"platform-b\",\"blockedRate\":2.6,\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-07\"}}"))
                .andExpect(jsonPath("$.data.severity").value("HIGH"));
        mockMvc.perform(
                        post("/mcp/tools/unknown/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"arguments\":{}}"))
                .andExpect(status().isNotFound())
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(containsString("Exception"))));
        mockMvc.perform(
                        post("/mcp/tools/get_platform_owner/call")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("trace"))));
    }
}
