package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionModeTest {
    @Test
    void llmReactWithoutModelConfigReturnsClearError() {
        EnterpriseAgentProperties properties = new EnterpriseAgentProperties();
        properties.setExecutionMode("llm-react");
        ObjectMapper objectMapper = new ObjectMapper();
        SkillRepository skillRepository = new SkillRepository(Path.of("skills"), 65536);
        AgentToolRegistry registry = new AgentToolRegistry();
        LlmReActAgent llmReActAgent =
                new LlmReActAgent(
                        properties,
                        request -> {
                            throw new AssertionError(
                                    "model client should not be called without config");
                        },
                        registry,
                        skillRepository,
                        (userId, tool) -> true,
                        new ToolCallValidator(objectMapper),
                        objectMapper);
        EnterpriseReActAgent agent =
                new EnterpriseReActAgent(
                        properties,
                        new EnterpriseTools(
                                new SmsApiProperties(),
                                java.net.http.HttpClient.newHttpClient(),
                                objectMapper,
                                new PythonSandbox(
                                        (command, timeout, limit) ->
                                                new ProcessResult(0, "", false, false))),
                        skillRepository,
                        registry,
                        llmReActAgent);
        AgentChatResult result = agent.chat(List.of(), "hello");
        assertEquals(ExecutionMode.LLM_REACT, result.executionMode());
        assertTrue(result.answer().contains("AGENT_MODEL"));
    }
}
