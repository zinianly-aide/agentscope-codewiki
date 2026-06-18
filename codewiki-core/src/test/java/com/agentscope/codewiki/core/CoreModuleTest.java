package com.agentscope.codewiki.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-core module. */
class CoreModuleTest {

    @Test
    void shouldInstantiate() {
        CoreModule module = new CoreModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
