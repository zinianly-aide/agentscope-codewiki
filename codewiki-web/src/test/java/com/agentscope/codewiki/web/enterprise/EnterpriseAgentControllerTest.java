package com.agentscope.codewiki.web.enterprise;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnterpriseAgentController.class)
@Import(EnterpriseAgentControllerTest.TestConfig.class)
class EnterpriseAgentControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void chatReturnsAnswerAndExecutionMode() throws Exception {
        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"userId":"E10086","sessionId":"s1","requestId":"req-1","message":"查询2026-07-01至2026-07-07短信异常"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.answer").value("ok"))
                .andExpect(jsonPath("$.executionMode").value("deterministic"));
    }

    @Test
    void chatRejectsMissingUserId() throws Exception {
        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"sessionId":"s1","requestId":"req-1","message":"hello"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId"));
    }

    @Test
    void chatRejectsMissingMessage() throws Exception {
        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"userId":"u1","sessionId":"s1","requestId":"req-1"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message"));
    }

    @Test
    void chatRejectsTooLongMessage() throws Exception {
        String body =
                "{\"userId\":\"u1\",\"sessionId\":\"s1\",\"requestId\":\"req-1\",\"message\":\""
                        + "x".repeat(10001)
                        + "\"}";
        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message"));
    }

    @Test
    void chatMapsAgentFailure() throws Exception {
        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"userId":"fail","sessionId":"s1","requestId":"req-1","message":"hello"}
                                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AGENT_ERROR"))
                .andExpect(jsonPath("$.message", containsString("boom")));
    }

    static class TestConfig {
        @Bean
        EnterpriseAgentService enterpriseAgentService() {
            return new EnterpriseAgentService(null, null) {
                @Override
                public AgentChatResult chat(ChatRequest request) {
                    if ("fail".equals(request.userId())) {
                        throw new EnterpriseAgentException("boom");
                    }
                    return new AgentChatResult("ok", ExecutionMode.DETERMINISTIC);
                }
            };
        }
    }
}
