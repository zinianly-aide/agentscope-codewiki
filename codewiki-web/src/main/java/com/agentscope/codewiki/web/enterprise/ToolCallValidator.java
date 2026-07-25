package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;

public class ToolCallValidator {
    private final ObjectMapper objectMapper;

    public ToolCallValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(AgentTool tool, String argumentsJson) {
        try {
            if (argumentsJson == null || argumentsJson.length() > 16_384) {
                return ValidationResult.invalid("arguments JSON is too large");
            }
            Map<String, Object> args =
                    objectMapper.readValue(
                            argumentsJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> schema = tool.inputSchema();
            Object requiredObject = schema.get("required");
            if (requiredObject instanceof java.util.List<?> required) {
                for (Object key : required) {
                    if (!args.containsKey(String.valueOf(key))) {
                        return ValidationResult.invalid(key + " is required");
                    }
                }
            }
            Object propsObject = schema.get("properties");
            if (propsObject instanceof Map<?, ?> props) {
                for (String key : args.keySet()) {
                    if (!props.containsKey(key)) {
                        return ValidationResult.invalid(key + " is not allowed");
                    }
                    Object value = args.get(key);
                    if (value instanceof String string && string.length() > 2048) {
                        return ValidationResult.invalid(key + " is too long");
                    }
                }
            }
            if ("query_sms_summary".equals(tool.name())) {
                validateDate(String.valueOf(args.get("startDate")), "startDate");
                validateDate(String.valueOf(args.get("endDate")), "endDate");
            }
            return ValidationResult.valid(args);
        } catch (IllegalArgumentException ex) {
            return ValidationResult.invalid(ex.getMessage());
        } catch (Exception ex) {
            return ValidationResult.invalid("arguments must be a valid JSON object");
        }
    }

    private static void validateDate(String value, String field) {
        try {
            LocalDate.parse(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field + " 必须使用 yyyy-MM-dd 格式");
        }
    }

    public record ValidationResult(
            boolean valid, Map<String, Object> arguments, String errorMessage) {
        static ValidationResult valid(Map<String, Object> arguments) {
            return new ValidationResult(true, arguments, null);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, Map.of(), errorMessage);
        }
    }
}
