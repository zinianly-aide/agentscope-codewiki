package com.agentscope.codewiki.web.enterprise;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the OpenAI-compatible enterprise agent runtime. */
@ConfigurationProperties(prefix = "enterprise.agent")
public class EnterpriseAgentProperties {
    private String model = "";
    private String apiKey = "";
    private String baseUrl = "";
    private int maxIters = 6;
    private String executionMode = "deterministic";
    private long maxSessions = 10000;
    private Duration sessionExpireAfterAccess = Duration.ofMinutes(30);
    private int maxTurnsPerSession = 10;
    private String skillsRoot = "skills";
    private long maxSkillFileBytes = 65536;
    private int maxToolCallsPerIteration = 3;
    private int maxToolCallsPerRequest = 8;
    private boolean pythonEnabled = true;
    private long maxModelResponseBytes = 1024 * 1024;
    private Async async = new Async();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = Math.max(5, Math.min(8, maxIters));
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode == null ? "deterministic" : executionMode;
    }

    public ExecutionMode executionMode() {
        return ExecutionMode.from(executionMode);
    }

    public long getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(long maxSessions) {
        this.maxSessions = Math.max(1, maxSessions);
    }

    public Duration getSessionExpireAfterAccess() {
        return sessionExpireAfterAccess;
    }

    public void setSessionExpireAfterAccess(Duration sessionExpireAfterAccess) {
        this.sessionExpireAfterAccess =
                sessionExpireAfterAccess == null
                        ? Duration.ofMinutes(30)
                        : sessionExpireAfterAccess;
    }

    public int getMaxTurnsPerSession() {
        return maxTurnsPerSession;
    }

    public void setMaxTurnsPerSession(int maxTurnsPerSession) {
        this.maxTurnsPerSession = Math.max(1, maxTurnsPerSession);
    }

    public String getSkillsRoot() {
        return skillsRoot;
    }

    public void setSkillsRoot(String skillsRoot) {
        this.skillsRoot = skillsRoot == null || skillsRoot.isBlank() ? "skills" : skillsRoot;
    }

    public long getMaxSkillFileBytes() {
        return maxSkillFileBytes;
    }

    public void setMaxSkillFileBytes(long maxSkillFileBytes) {
        this.maxSkillFileBytes = Math.max(1024, maxSkillFileBytes);
    }

    public int getMaxToolCallsPerIteration() {
        return maxToolCallsPerIteration;
    }

    public void setMaxToolCallsPerIteration(int maxToolCallsPerIteration) {
        this.maxToolCallsPerIteration = Math.max(1, Math.min(5, maxToolCallsPerIteration));
    }

    public int getMaxToolCallsPerRequest() {
        return maxToolCallsPerRequest;
    }

    public void setMaxToolCallsPerRequest(int maxToolCallsPerRequest) {
        this.maxToolCallsPerRequest = Math.max(1, Math.min(16, maxToolCallsPerRequest));
    }

    public boolean isPythonEnabled() {
        return pythonEnabled;
    }

    public void setPythonEnabled(boolean pythonEnabled) {
        this.pythonEnabled = pythonEnabled;
    }

    public long getMaxModelResponseBytes() {
        return maxModelResponseBytes;
    }

    public void setMaxModelResponseBytes(long maxModelResponseBytes) {
        this.maxModelResponseBytes = Math.max(4096, maxModelResponseBytes);
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async == null ? new Async() : async;
    }

    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = Math.max(1, corePoolSize);
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = Math.max(corePoolSize, maxPoolSize);
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = Math.max(1, queueCapacity);
        }
    }

    public boolean modelConfigured() {
        return !model.isBlank() && !apiKey.isBlank() && !baseUrl.isBlank();
    }
}
