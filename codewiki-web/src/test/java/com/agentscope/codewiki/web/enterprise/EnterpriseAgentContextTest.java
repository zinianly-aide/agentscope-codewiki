package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EnterpriseAgentContextTest {
    @Autowired private EnterpriseAgentService service;
    @Autowired private EnterpriseAgentProperties properties;

    @Test
    void contextStartsAndAgentCanAnswerWithMockSmsData() {
        AgentChatResult answer =
                service.chat(
                        new ChatRequest(
                                "E10086",
                                "sms-analysis-001",
                                "req-001",
                                "查询2026年7月1日至7月7日短信敏感词拦截情况"));
        assertEquals(ExecutionMode.DETERMINISTIC, answer.executionMode());
        assertTrue(answer.answer().contains("platform-b"));
        assertTrue(answer.answer().contains("数据来源"));
    }

    @Test
    void defaultExecutionModeIsDeterministic() {
        assertEquals(ExecutionMode.DETERMINISTIC, properties.executionMode());
    }

    @Test
    void mdcIsClearedAfterRequest() {
        service.chat(new ChatRequest("E10086", "mdc-session", "req-mdc", "你好"));
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
        assertNull(MDC.get("sessionId"));
    }
}
