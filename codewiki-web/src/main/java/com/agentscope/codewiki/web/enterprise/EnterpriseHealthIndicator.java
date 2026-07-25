package com.agentscope.codewiki.web.enterprise;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Sanitized enterprise-agent health details. */
public class EnterpriseHealthIndicator implements HealthIndicator {
    private final DockerHealthService dockerHealthService;
    private final SmsApiProperties smsApiProperties;
    private final SkillRepository skillRepository;
    private final McpClient mcpClient;

    public EnterpriseHealthIndicator(
            DockerHealthService dockerHealthService,
            SmsApiProperties smsApiProperties,
            SkillRepository skillRepository,
            McpClient mcpClient) {
        this.dockerHealthService = dockerHealthService;
        this.smsApiProperties = smsApiProperties;
        this.skillRepository = skillRepository;
        this.mcpClient = mcpClient;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("dockerAvailable", dockerHealthService.dockerAvailable())
                .withDetail("pythonImagePresent", dockerHealthService.pythonImagePresent())
                .withDetail("smsApiMode", smsApiProperties.isMockEnabled() ? "mock" : "real")
                .withDetail("skillCount", skillRepository.skillCount())
                .withDetail("mcpDemoStatus", mcpClient.available() ? "available" : "unavailable")
                .withDetail("mcpDemoDetail", mcpClient.status())
                .build();
    }
}
