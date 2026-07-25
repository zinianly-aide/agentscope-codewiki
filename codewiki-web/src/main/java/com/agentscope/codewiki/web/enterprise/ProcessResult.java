package com.agentscope.codewiki.web.enterprise;

/** Result of an isolated process execution. */
public record ProcessResult(int exitCode, String output, boolean timedOut, boolean truncated) {}
