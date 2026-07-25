package com.agentscope.codewiki.web.enterprise;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Read-only SMS summary API configuration. */
@ConfigurationProperties(prefix = "enterprise.sms-api")
public class SmsApiProperties {
    private String baseUrl = "http://127.0.0.1:8081";
    private String token = "";
    private boolean mockEnabled = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }
}
