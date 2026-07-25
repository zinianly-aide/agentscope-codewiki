package com.agentscope.codewiki.web.enterprise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnterpriseToolsTest {
    @Test
    void querySmsSummaryReturnsToolResultForMockData() {
        ToolResult<SmsSummary> result =
                tools(new ProcessResult(0, "", false, false))
                        .querySmsSummary("2026-07-01", "2026-07-07");
        assertTrue(result.success());
        assertEquals("query_sms_summary", result.toolName());
        assertEquals("mock:sms-api", result.source());
        assertEquals(12000, result.data().totalSubmitted());
        assertEquals("platform-b", result.data().platforms().get(1).platform());
    }

    @Test
    void querySmsSummaryRejectsInvalidDateFormat() {
        ToolResult<SmsSummary> result =
                tools(new ProcessResult(0, "", false, false))
                        .querySmsSummary("2026/07/01", "2026-07-07");
        assertFalse(result.success());
        assertEquals("INVALID_DATE_RANGE", result.errorCode());
        assertTrue(result.errorMessage().contains("YYYY-MM-DD"));
    }

    @Test
    void querySmsSummaryRejectsEndBeforeStart() {
        ToolResult<SmsSummary> result =
                tools(new ProcessResult(0, "", false, false))
                        .querySmsSummary("2026-07-07", "2026-07-01");
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("endDate"));
    }

    @Test
    void querySmsSummaryRejectsMoreThanNinetyDays() {
        ToolResult<SmsSummary> result =
                tools(new ProcessResult(0, "", false, false))
                        .querySmsSummary("2026-01-01", "2026-04-15");
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("90"));
    }

    @Test
    void querySmsSummaryDoesNotExposeTokenInError() {
        SmsApiProperties props = new SmsApiProperties();
        props.setMockEnabled(false);
        props.setBaseUrl("http://[bad-url]");
        props.setToken("secret-token");
        EnterpriseTools tools =
                new EnterpriseTools(
                        props,
                        java.net.http.HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        new PythonSandbox(
                                (command, timeout, limit) ->
                                        new ProcessResult(0, "", false, false)));
        assertFalse(
                tools.querySmsSummary("2026-07-01", "2026-07-07")
                        .errorMessage()
                        .contains("secret-token"));
    }

    @Test
    void runPythonReturnsToolResultOutput() {
        ToolResult<String> result =
                tools(new ProcessResult(0, "2\n", false, false)).runPython("print(1 + 1)");
        assertTrue(result.success());
        assertEquals("run_python", result.toolName());
        assertTrue(result.data().contains("2"));
    }

    @Test
    void runPythonRejectsBlankCode() {
        ToolResult<String> result = tools(new ProcessResult(0, "", false, false)).runPython(" ");
        assertFalse(result.success());
        assertEquals("EMPTY_CODE", result.errorCode());
    }

    @Test
    void runPythonRejectsTooLongCode() {
        ToolResult<String> result =
                tools(new ProcessResult(0, "", false, false)).runPython("x".repeat(8001));
        assertFalse(result.success());
        assertEquals("CODE_TOO_LARGE", result.errorCode());
    }

    @Test
    void runPythonReportsTimeout() {
        ToolResult<String> result =
                tools(new ProcessResult(-1, "", true, false)).runPython("while True: pass");
        assertFalse(result.success());
        assertEquals("TIMEOUT", result.errorCode());
    }

    @Test
    void runPythonReportsTruncation() {
        ToolResult<String> result =
                tools(new ProcessResult(0, "x", false, true)).runPython("print('x')");
        assertTrue(result.success());
        assertTrue(result.data().contains("truncated"));
    }

    @Test
    void runPythonReportsDockerUnavailable() {
        PythonSandbox sandbox =
                new PythonSandbox(
                        new ProcessExecutor() {
                            @Override
                            public ProcessResult execute(
                                    List<String> command, Duration timeout, int outputLimitBytes)
                                    throws IOException {
                                throw new IOException("docker missing");
                            }
                        });
        ToolResult<String> result = sandbox.runPython("print(1)");
        assertFalse(result.success());
        assertEquals("DOCKER_UNAVAILABLE", result.errorCode());
    }

    @Test
    void dockerCommandIsHardenedAndDoesNotPassHostSecrets() {
        PythonSandbox sandbox =
                new PythonSandbox(
                        (command, timeout, limit) -> new ProcessResult(0, "", false, false));
        List<String> command = sandbox.buildDockerCommand("print(1)");
        assertTrue(command.contains("--cap-drop=ALL"));
        assertTrue(command.contains("--security-opt=no-new-privileges"));
        assertTrue(command.contains("--user=65534:65534"));
        assertFalse(command.contains("AGENT_API_KEY"));
        assertFalse(command.contains("SMS_API_TOKEN"));
        assertFalse(command.contains("Authorization"));
        assertNotNull(command);
    }

    private static EnterpriseTools tools(ProcessResult result) {
        SmsApiProperties props = new SmsApiProperties();
        props.setMockEnabled(true);
        return new EnterpriseTools(
                props,
                java.net.http.HttpClient.newHttpClient(),
                new ObjectMapper(),
                new PythonSandbox((command, timeout, limit) -> result));
    }
}
