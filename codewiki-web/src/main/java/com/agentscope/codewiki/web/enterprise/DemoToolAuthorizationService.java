package com.agentscope.codewiki.web.enterprise;

public class DemoToolAuthorizationService implements ToolAuthorizationService {
    private final EnterpriseAgentProperties properties;

    public DemoToolAuthorizationService(EnterpriseAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAllowed(String userId, AgentTool tool) {
        if (tool.source() == ToolSource.PYTHON) {
            return properties.isPythonEnabled();
        }
        return true;
    }
}
