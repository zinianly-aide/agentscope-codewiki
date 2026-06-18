package com.agentscope.codewiki.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-agent module. */
class AgentModuleTest {

    @Test
    void shouldInstantiate() {
        AgentModule module = new AgentModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
