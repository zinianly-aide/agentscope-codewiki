package com.agentscope.codewiki.web.enterprise;

public interface ToolAuthorizationService {
    boolean isAllowed(String userId, AgentTool tool);
}
