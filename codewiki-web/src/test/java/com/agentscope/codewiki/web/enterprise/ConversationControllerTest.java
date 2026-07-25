package com.agentscope.codewiki.web.enterprise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversationController.class)
@Import(ConversationControllerTest.TestConfig.class)
class ConversationControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void createReturnsConversationIdAndEventsUrl() throws Exception {
        mockMvc.perform(
                        post("/api/agent/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"userId":"E10086","sessionId":"s1","requestId":"req-1","message":"查询2026-07-01至2026-07-07短信异常"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.eventsUrl").value("/api/agent/conversations/conv-1/events"));
    }

    @Test
    void getReturnsSnapshotWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/agent/conversations/conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.answer").value("done"))
                .andExpect(jsonPath("$.events[0].type").value("TOOL_CALL_COMPLETED"))
                .andExpect(jsonPath("$.events[0].metadata.token").doesNotExist())
                .andExpect(jsonPath("$.events[0].metadata.toolName").value("query_sms_summary"));
    }

    @Test
    void eventsEndpointStartsSseStream() throws Exception {
        mockMvc.perform(get("/api/agent/conversations/conv-1/events"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    @Test
    void unknownConversationReturns404() throws Exception {
        mockMvc.perform(get("/api/agent/conversations/missing")).andExpect(status().isNotFound());
    }

    static class TestConfig {
        @Bean
        ConversationService conversationService() {
            return new ConversationService(null, null) {
                @Override
                public ConversationExecution start(ChatRequest request) {
                    return execution();
                }

                @Override
                public Optional<ConversationExecution> find(String id) {
                    return "conv-1".equals(id) ? Optional.of(execution()) : Optional.empty();
                }

                private ConversationExecution execution() {
                    ConversationExecution execution =
                            new ConversationExecution("conv-1", "req-1", 50);
                    execution.answer("done");
                    execution.status(ConversationStatus.COMPLETED);
                    execution.append(
                            AgentEventType.TOOL_CALL_COMPLETED,
                            "Tool 执行完成",
                            "query_sms_summary 执行完成",
                            AgentEventStatus.COMPLETED,
                            75,
                            Map.of("toolName", "query_sms_summary"));
                    return execution;
                }
            };
        }
    }
}
