package com.agentscope.codewiki.agent;

/** Runtime exception thrown when repository wiki generation fails. */
public class WikiGenerationException extends RuntimeException {

    public WikiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
