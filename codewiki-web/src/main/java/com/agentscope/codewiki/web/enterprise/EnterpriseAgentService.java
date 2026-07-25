package com.agentscope.codewiki.web.enterprise;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Service boundary for session isolation, request timeout, and safe error conversion. */
public class EnterpriseAgentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnterpriseAgentService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private final EnterpriseReActAgent enterpriseAgent;
    private final EnterpriseSessionStore sessionStore;

    public EnterpriseAgentService(
            EnterpriseReActAgent enterpriseAgent, EnterpriseSessionStore sessionStore) {
        this.enterpriseAgent = enterpriseAgent;
        this.sessionStore = sessionStore;
    }

    public AgentChatResult chat(ChatRequest request) {
        putMdc(request);
        try {
            EnterpriseSession session = sessionStore.get(request.userId(), request.sessionId());
            Map<String, String> parentMdc = MDC.getCopyOfContextMap();
            AgentChatResult result =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        setMdc(parentMdc);
                                        try {
                                            synchronized (session) {
                                                AgentChatResult answer =
                                                        enterpriseAgent.chat(
                                                                session.snapshot(),
                                                                request.message(),
                                                                new ToolExecutionContext(
                                                                        request.requestId()));
                                                session.add(request.message(), answer.answer());
                                                return answer;
                                            }
                                        } finally {
                                            MDC.clear();
                                        }
                                    })
                            .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            LOGGER.info(
                    "enterprise agent request completed executionMode={}",
                    result.executionMode().wireValue());
            return result;
        } catch (java.util.concurrent.TimeoutException ex) {
            LOGGER.warn("enterprise agent request timed out");
            throw new EnterpriseAgentException("Agent request timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EnterpriseAgentException("Agent request interrupted");
        } catch (EnterpriseAgentException ex) {
            LOGGER.warn("enterprise agent request rejected: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            LOGGER.warn("enterprise agent request failed: {}", ex.toString());
            throw new EnterpriseAgentException("Agent request failed");
        } finally {
            MDC.clear();
        }
    }

    long sessionCount() {
        return sessionStore.estimatedSize();
    }

    private static void putMdc(ChatRequest request) {
        MDC.put("requestId", request.requestId());
        MDC.put("userId", request.userId());
        MDC.put("sessionId", request.sessionId());
    }

    private static void setMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
