package com.agentscope.codewiki.mcp;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DemoMcpToolRegistry {
    private final Validator validator;
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

    public DemoMcpToolRegistry(Validator validator) {
        this.validator = validator;
    }

    public List<McpToolDescriptor> descriptors() {
        return descriptors;
    }

    public boolean registered(String toolName) {
        return descriptors.stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    public McpToolCallResponse<?> call(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "get_platform_owner" -> getPlatformOwner(toOwnerRequest(args));
            case "create_sms_alert_summary" -> createSmsAlertSummary(toAlertRequest(args));
            default -> McpToolCallResponse.failure(toolName, "TOOL_NOT_FOUND", "unknown MCP tool");
        };
    }

    private McpToolCallResponse<?> getPlatformOwner(@Valid OwnerRequest request) {
        String validation = validate(request);
        if (!validation.isBlank()) {
            return McpToolCallResponse.failure(
                    "get_platform_owner", "INVALID_ARGUMENTS", validation);
        }
        String owner =
                switch (request.platform()) {
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
                        request.platform(),
                        "ownerName",
                        owner,
                        "ownerEmail",
                        request.platform() + "-owner@example.local",
                        "department",
                        "Messaging Platform Team"));
    }

    private McpToolCallResponse<?> createSmsAlertSummary(@Valid AlertRequest request) {
        String validation = validate(request);
        if (!validation.isBlank()
                || !validDate(request.startDate())
                || !validDate(request.endDate())) {
            return McpToolCallResponse.failure(
                    "create_sms_alert_summary",
                    "INVALID_ARGUMENTS",
                    "platform, blockedRate, startDate and endDate are required");
        }
        String severity =
                request.blockedRate() < 1.0
                        ? "LOW"
                        : request.blockedRate() <= 2.0 ? "MEDIUM" : "HIGH";
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
                                request.platform(),
                                request.startDate(),
                                request.endDate(),
                                request.blockedRate()),
                        "recommendedActions",
                        List.of("检查近期新增短信模板", "核对运营商回执中的敏感词原因", "通知平台负责人评估")));
    }

    private OwnerRequest toOwnerRequest(Map<String, Object> args) {
        return new OwnerRequest(asText(args.get("platform")));
    }

    private AlertRequest toAlertRequest(Map<String, Object> args) {
        return new AlertRequest(
                asText(args.get("platform")),
                asDouble(args.get("blockedRate")),
                asText(args.get("startDate")),
                asText(args.get("endDate")));
    }

    private String validate(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        return violations.isEmpty() ? "" : "invalid MCP arguments";
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

    record OwnerRequest(@NotBlank String platform) {}

    record AlertRequest(
            @NotBlank String platform,
            @NotNull @DecimalMin("0.0") Double blockedRate,
            @NotBlank String startDate,
            @NotBlank String endDate) {}
}
