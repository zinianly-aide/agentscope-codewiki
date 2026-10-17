package com.agentscope.codewiki.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StandardMcpControllerTest {
    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rpc(
            String json) {
        return post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "application/json, text/event-stream")
                .content(json);
    }

    @Test
    void supportsStatelessInitializeListAndCall() throws Exception {
        mvc.perform(
                        rpc(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(false));
        mvc.perform(rpc("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("get_platform_owner")));
        mvc.perform(
                        rpc(
                                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_platform_owner\",\"arguments\":{\"platform\":\"platform-b\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(content().string(containsString("ownerName")));
    }

    @Test
    void rejectsInvalidJsonRpcAndUnknownMethodsOrTools() throws Exception {
        mvc.perform(rpc("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(jsonPath("$.error.code").value(-32600));
        mvc.perform(rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"missing\"}"))
                .andExpect(jsonPath("$.error.code").value(-32601));
        mvc.perform(
                        rpc(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"missing\"}}"))
                .andExpect(jsonPath("$.result.isError").value(true));
    }
}
