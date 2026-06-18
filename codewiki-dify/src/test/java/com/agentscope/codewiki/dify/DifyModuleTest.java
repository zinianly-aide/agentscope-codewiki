package com.agentscope.codewiki.dify;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-dify module. */
class DifyModuleTest {

    @Test
    void shouldInstantiate() {
        DifyModule module = new DifyModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
