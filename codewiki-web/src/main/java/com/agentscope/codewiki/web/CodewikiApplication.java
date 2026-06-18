package com.agentscope.codewiki.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the AgentScope CodeWiki web application. */
@SpringBootApplication
public class CodewikiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodewikiApplication.class, args);
    }
}
