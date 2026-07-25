package com.agentscope.codewiki.web.enterprise;

public class ModelClientException extends RuntimeException {
    private final String errorCode;

    public ModelClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
