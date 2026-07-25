package com.agentscope.codewiki.web.enterprise;

import java.util.List;
import java.util.Map;

public class LocalPythonTool implements AgentTool {
    private final EnterpriseTools enterpriseTools;

    public LocalPythonTool(EnterpriseTools enterpriseTools) {
        this.enterpriseTools = enterpriseTools;
    }

    @Override
    public String name() {
        return "run_python";
    }

    @Override
    public String description() {
        return "在 Docker Python 沙箱中执行精确计算";
    }

    @Override
    public ToolSource source() {
        return ToolSource.PYTHON;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of("code", Map.of("type", "string")),
                "required",
                List.of("code"));
    }

    @Override
    public ToolResult<?> execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return enterpriseTools.runPython(String.valueOf(arguments.getOrDefault("code", "")));
    }
}
