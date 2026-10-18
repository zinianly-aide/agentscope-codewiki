package com.agentscope.codewiki.parser;

/** Source-located AST call site; null receiver means an unqualified invocation. */
public record CodeReference(String kind, String name, String receiver, int line) {}
