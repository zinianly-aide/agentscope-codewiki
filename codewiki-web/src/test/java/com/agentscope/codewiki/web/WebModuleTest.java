package com.agentscope.codewiki.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Unit test for the codewiki-web module. */
class WebModuleTest {

    @Test
    void shouldInstantiate() {
        CodewikiApplication module = new CodewikiApplication();
        assertNotNull(module, "Module should be instantiated");
    }
}
