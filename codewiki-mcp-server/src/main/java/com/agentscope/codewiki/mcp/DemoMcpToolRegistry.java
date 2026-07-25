package com.agentscope.codewiki.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DemoMcpToolRegistry {
    private final List<McpToolDescriptor> descriptors =
            List.of(
                    new McpToolDescriptor(
                            "get_platform_owner",
                            "查询短信平台负责人",
                            Map.of(
                                    "type", "object",
                                    "properties", Map.of("platform", Map.of("type", "string")),
                                    "required", List.of("platform"))),
                    new McpToolDescriptor(
                            "create_sms_alert_summary",
                            "生成短信敏感词拦截告警摘要",
                            Map.of(
                                    "type", "object",
                                    "properties",
                                            Map.of(
                                                    "platform", Map.of("type", "string"),
                                                    "blockedRate", Map.of("type", "number"),
                                                    "startDate", Map.of("type", "string"),
                                                    "endDate", Map.of("type", "string")),
                                    "required",
                                            List.of(
                                                    "platform",
                                                    "blockedRate",
                                                    "startDate",
                                                    "endDate"))));

    public List<McpToolDescriptor> descriptors() {
        return descriptors;
    }

    public McpToolCallResponse<?> call(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "get_platform_owner" -> getPlatformOwner(args);
            case "create_sms_alert_summary" -> createSmsAlertSummary(args);
            default -> McpToolCallResponse.failure(toolName, "TOOL_NOT_FOUND", "unknown MCP tool");
        };
    }

    private McpToolCallResponse<?> getPlatformOwner(Map<String, Object> args) {
        String platform = asText(args.get("platform"));
        if (platform.isBlank()) {
            return McpToolCallResponse.failure(
                    "get_platform_owner", "INVALID_ARGUMENTS", "platform is required");
        }
        String owner =
                switch (platform) {
                    case "platform-a" -> "李明";
                    case "platform-b" -> "张三";
                    case "platform-c" -> "王芳";
                    default -> null;
                };
        if (owner == null) {
            return McpToolCallResponse.failure(
                    "get_platform_owner", "PLATFORM_NOT_FOUND", "platform is unknown");
        }
        return McpToolCallResponse.success(
                "get_platform_owner",
                Map.of(
                        "platform",
                        platform,
                        "ownerName",
                        owner,
                        "ownerEmail",
                        platform + "-owner@example.local",
                        "department",
                        "Messaging Platform Team"));
    }

    private McpToolCallResponse<?> createSmsAlertSummary(Map<String, Object> args) {
        String platform = asText(args.get("platform"));
        Double blockedRate = asDouble(args.get("blockedRate"));
        String startDate = asText(args.get("startDate"));
        String endDate = asText(args.get("endDate"));
        if (platform.isBlank()
                || blockedRate == null
                || !validDate(startDate)
                || !validDate(endDate)) {
            return McpToolCallResponse.failure(
                    "create_sms_alert_summary",
                    "INVALID_ARGUMENTS",
                    "platform, blockedRate, startDate and endDate are required");
        }
        String severity = blockedRate < 1.0 ? "LOW" : blockedRate <= 2.0 ? "MEDIUM" : "HIGH";
        return McpToolCallResponse.success(
                "create_sms_alert_summary",
                Map.of(
                        "severity",
                        severity,
                        "title",
                        "短信敏感词拦截率异常",
                        "summary",
                        String.format(
                                Locale.ROOT,
                                "%s 在 %s 至 %s 的敏感词拦截率为 %.2f%%，建议平台负责人检查模板内容及运营商敏感词策略。",
                                platform,
                                startDate,
                                endDate,
                                blockedRate),
                        "recommendedActions",
                        List.of("检查近期新增短信模板", "核对运营商回执中的敏感词原因", "通知平台负责人评估")));
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean validDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
