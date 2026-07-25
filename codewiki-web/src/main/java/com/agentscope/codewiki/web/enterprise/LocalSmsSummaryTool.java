package com.agentscope.codewiki.web.enterprise;

import java.util.List;
import java.util.Map;

public class LocalSmsSummaryTool implements AgentTool {
    private final EnterpriseTools enterpriseTools;

    public LocalSmsSummaryTool(EnterpriseTools enterpriseTools) {
        this.enterpriseTools = enterpriseTools;
    }

    @Override
    public String name() {
        return "query_sms_summary";
    }

    @Override
    public String description() {
        return "查询短信平台统计摘要";
    }

    @Override
    public ToolSource source() {
        return ToolSource.LOCAL_JAVA;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties",
                        Map.of(
                                "startDate",
                                Map.of("type", "string"),
                                "endDate",
                                Map.of("type", "string")),
                "required", List.of("startDate", "endDate"));
    }

    @Override
    public ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return enterpriseTools.querySmsSummary(
                string(arguments, "startDate"), string(arguments, "endDate"));
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
