package com.agentscope.codewiki.parser;

/** Source-located AST invocation with optional conservatively resolved receiver type. */
public record CodeReference(
        String kind, String name, String receiver, int line, String receiverType) {
    public CodeReference(String kind, String name, String receiver, int line) {
        this(kind, name, receiver, line, null);
    }
}
