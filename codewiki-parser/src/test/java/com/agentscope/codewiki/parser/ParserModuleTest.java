package com.agentscope.codewiki.parser;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-parser module. */
class ParserModuleTest {

    @Test
    void shouldInstantiate() {
        ParserModule module = new ParserModule();
        assertNotNull(module, "Module should be instantiated");
    }
}
