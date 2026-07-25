package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Toolkit registered for the enterprise ReAct agent. All tools are read-only. */
public class EnterpriseTools {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(EnterpriseTools.class);
    private final SmsApiProperties smsApiProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PythonSandbox pythonSandbox;

    public EnterpriseTools(
            SmsApiProperties smsApiProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            PythonSandbox pythonSandbox) {
        this.smsApiProperties = smsApiProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.pythonSandbox = pythonSandbox;
    }

    public ToolResult<SmsSummary> querySmsSummary(String startDate, String endDate) {
        long start = System.nanoTime();
        String toolName = "query_sms_summary";
        DateRange range = validateRange(startDate, endDate);
        if (!range.valid()) {
            return logAndReturn(
                    ToolResult.failure(
                            toolName,
                            "sms-api",
                            elapsed(start),
                            "INVALID_DATE_RANGE",
                            range.error()));
        }
        if (smsApiProperties.isMockEnabled()) {
            return logAndReturn(
                    ToolResult.success(
                            toolName,
                            mockSmsSummary(range.startDate(), range.endDate()),
                            "mock:sms-api",
                            elapsed(start)));
        }
        if (smsApiProperties.getBaseUrl().isBlank()) {
            return logAndReturn(
                    ToolResult.failure(
                            toolName,
                            "sms-api",
                            elapsed(start),
                            "SMS_API_NOT_CONFIGURED",
                            "SMS API base-url is not configured"));
        }
        try {
            String uri = buildSmsUri(range.startDate(), range.endDate());
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(URI.create(uri))
                            .timeout(HTTP_TIMEOUT)
                            .GET()
                            .header("Accept", "application/json");
            if (!smsApiProperties.getToken().isBlank()) {
                builder.header("Authorization", "Bearer " + smsApiProperties.getToken());
            }
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return logAndReturn(
                        ToolResult.failure(
                                toolName,
                                "sms-api",
                                elapsed(start),
                                "SMS_API_HTTP_ERROR",
                                "SMS API returned HTTP " + response.statusCode()));
            }
            SmsSummary summary = objectMapper.readValue(response.body(), SmsSummary.class);
            return logAndReturn(ToolResult.success(toolName, summary, "sms-api", elapsed(start)));
        } catch (IOException ex) {
            return logAndReturn(
                    ToolResult.failure(
                            toolName,
                            "sms-api",
                            elapsed(start),
                            "SMS_API_REQUEST_FAILED",
                            "SMS API request failed"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return logAndReturn(
                    ToolResult.failure(
                            toolName,
                            "sms-api",
                            elapsed(start),
                            "SMS_API_INTERRUPTED",
                            "SMS API request interrupted"));
        } catch (IllegalArgumentException ex) {
            return logAndReturn(
                    ToolResult.failure(
                            toolName,
                            "sms-api",
                            elapsed(start),
                            "SMS_API_INVALID_CONFIG",
                            "SMS API configuration is invalid"));
        }
    }

    public ToolResult<String> runPython(String code) {
        return pythonSandbox.runPython(code);
    }

    private String buildSmsUri(LocalDate startDate, LocalDate endDate) {
        String base = smsApiProperties.getBaseUrl();
        String separator = base.endsWith("/") ? "" : "/";
        return base
                + separator
                + "internal/sms/summary?startDate="
                + URLEncoder.encode(startDate.toString(), StandardCharsets.UTF_8)
                + "&endDate="
                + URLEncoder.encode(endDate.toString(), StandardCharsets.UTF_8);
    }

    private static SmsSummary mockSmsSummary(LocalDate startDate, LocalDate endDate) {
        return new SmsSummary(
                startDate.toString(),
                endDate.toString(),
                12000,
                11340,
                180,
                List.of(
                        new SmsSummary.PlatformMetric("platform-a", 7000, 6790, 50),
                        new SmsSummary.PlatformMetric("platform-b", 5000, 4550, 130)));
    }

    private static DateRange validateRange(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (end.isBefore(start)) {
                return DateRange.invalid("endDate must not be earlier than startDate");
            }
            if (ChronoUnit.DAYS.between(start, end) > 90) {
                return DateRange.invalid("query range must not exceed 90 days");
            }
            return new DateRange(start, end, null);
        } catch (DateTimeParseException ex) {
            return DateRange.invalid("dates must use YYYY-MM-DD format");
        }
    }

    private static <T> ToolResult<T> logAndReturn(ToolResult<T> result) {
        MDC.put("toolName", result.toolName());
        MDC.put("durationMs", Long.toString(result.durationMs()));
        MDC.put("resultStatus", result.success() ? "success" : "failure");
        try {
            LOGGER.info(
                    "tool execution completed toolName={} skillName={} iteration={} durationMs={} resultStatus={}",
                    result.toolName(),
                    MDC.get("skillName"),
                    MDC.get("iteration"),
                    result.durationMs(),
                    MDC.get("resultStatus"));
            return result;
        } finally {
            MDC.remove("toolName");
            MDC.remove("durationMs");
            MDC.remove("resultStatus");
        }
    }

    private static long elapsed(long start) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate, String error) {
        static DateRange invalid(String error) {
            return new DateRange(null, null, error);
        }

        boolean valid() {
            return error == null;
        }
    }
}
