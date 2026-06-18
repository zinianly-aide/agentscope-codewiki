package com.agentscope.codewiki.dify;

/** Exception thrown when the Dify API returns a non-200 response. */
public class DifyException extends RuntimeException {

    public DifyException(String message) {
        super(message);
    }

    public DifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
