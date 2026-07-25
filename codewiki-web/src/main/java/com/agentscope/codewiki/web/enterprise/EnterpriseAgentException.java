package com.agentscope.codewiki.web.enterprise;

/** Business exception that hides framework stacks and secrets from API clients. */
public class EnterpriseAgentException extends RuntimeException {
    public EnterpriseAgentException(String message) {
        super(message);
    }

    public EnterpriseAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
