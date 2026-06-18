package com.agentscope.codewiki.retriever;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-retriever module. */
class RetrieverModuleTest {

    @Test
    void shouldInstantiate() {
        RetrieverModule module = new RetrieverModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
