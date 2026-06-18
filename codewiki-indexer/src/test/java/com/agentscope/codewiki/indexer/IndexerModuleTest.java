package com.agentscope.codewiki.indexer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-indexer module. */
class IndexerModuleTest {

    @Test
    void shouldInstantiate() {
        IndexerModule module = new IndexerModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
