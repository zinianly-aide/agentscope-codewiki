package com.agentscope.codewiki.web.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Spring beans for the single enterprise ReAct agent and its toolkit. */
@Configuration
@EnableConfigurationProperties({
    EnterpriseAgentProperties.class,
    SmsApiProperties.class,
    ConversationProperties.class,
    McpServerProperties.class
})
public class EnterpriseAgentConfiguration {
    @Bean
    ProcessExecutor processExecutor() {
        return new DefaultProcessExecutor();
    }

    @Bean
    PythonSandbox pythonSandbox(ProcessExecutor processExecutor) {
        return new PythonSandbox(processExecutor);
    }

    @Bean
    DockerHealthService dockerHealthService(ProcessExecutor processExecutor) {
        return new DockerHealthService(processExecutor);
    }

    @Bean
    EnterpriseTools enterpriseTools(
            SmsApiProperties smsApiProperties,
            ObjectMapper objectMapper,
            PythonSandbox pythonSandbox) {
        return new EnterpriseTools(
                smsApiProperties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                objectMapper,
                pythonSandbox);
    }

    @Bean
    McpClient mcpClient(McpServerProperties properties, ObjectMapper objectMapper) {
        HttpMcpClient client = new HttpMcpClient(properties, objectMapper);
        client.discoverTools();
        return client;
    }

    @Bean
    AgentToolRegistry agentToolRegistry(EnterpriseTools enterpriseTools, McpClient mcpClient) {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.register(new LocalSmsSummaryTool(enterpriseTools));
        registry.register(new LocalPythonTool(enterpriseTools));
        for (McpToolDescriptor descriptor : mcpClient.discoverTools().tools()) {
            registry.register(new McpAgentTool(descriptor, mcpClient));
        }
        return registry;
    }

    @Bean
    AgentModelClient agentModelClient(
            EnterpriseAgentProperties properties, ObjectMapper objectMapper) {
        return new OpenAiCompatibleAgentModelClient(properties, objectMapper);
    }

    @Bean
    ToolCallValidator toolCallValidator(ObjectMapper objectMapper) {
        return new ToolCallValidator(objectMapper);
    }

    @Bean
    ToolAuthorizationService toolAuthorizationService(EnterpriseAgentProperties properties) {
        return new DemoToolAuthorizationService(properties);
    }

    @Bean
    SkillRepository skillRepository(EnterpriseAgentProperties properties) {
        Path configured = Path.of(properties.getSkillsRoot());
        Path parentSkills = Path.of("..", properties.getSkillsRoot());
        Path selected = Files.isDirectory(configured) ? configured : parentSkills;
        return new SkillRepository(selected, properties.getMaxSkillFileBytes());
    }

    @Bean
    LlmReActAgent llmReActAgent(
            EnterpriseAgentProperties properties,
            AgentModelClient agentModelClient,
            AgentToolRegistry agentToolRegistry,
            SkillRepository skillRepository,
            ToolAuthorizationService toolAuthorizationService,
            ToolCallValidator toolCallValidator,
            ObjectMapper objectMapper) {
        return new LlmReActAgent(
                properties,
                agentModelClient,
                agentToolRegistry,
                skillRepository,
                toolAuthorizationService,
                toolCallValidator,
                objectMapper);
    }

    @Bean(name = "enterpriseAgent")
    EnterpriseReActAgent enterpriseAgent(
            EnterpriseAgentProperties properties,
            EnterpriseTools tools,
            SkillRepository skillRepository,
            AgentToolRegistry agentToolRegistry,
            LlmReActAgent llmReActAgent) {
        return new EnterpriseReActAgent(
                properties, tools, skillRepository, agentToolRegistry, llmReActAgent);
    }

    @Bean
    EnterpriseSessionStore enterpriseSessionStore(EnterpriseAgentProperties properties) {
        return new EnterpriseSessionStore(properties);
    }

    @Bean
    EnterpriseAgentService enterpriseAgentService(
            EnterpriseReActAgent enterpriseAgent, EnterpriseSessionStore enterpriseSessionStore) {
        return new EnterpriseAgentService(enterpriseAgent, enterpriseSessionStore);
    }

    @Bean
    ConversationStore conversationStore(ConversationProperties properties) {
        return new InMemoryConversationStore(properties);
    }

    @Bean
    ThreadPoolTaskExecutor enterpriseAgentTaskExecutor(EnterpriseAgentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("enterprise-agent-");
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean
    ConversationService conversationService(
            ConversationStore conversationStore,
            EnterpriseAgentService enterpriseAgentService,
            ThreadPoolTaskExecutor enterpriseAgentTaskExecutor) {
        return new ConversationService(
                conversationStore, enterpriseAgentService, enterpriseAgentTaskExecutor);
    }

    @Bean
    EnterpriseHealthIndicator enterpriseHealthIndicator(
            DockerHealthService dockerHealthService,
            SmsApiProperties smsApiProperties,
            SkillRepository skillRepository,
            McpClient mcpClient) {
        return new EnterpriseHealthIndicator(
                dockerHealthService, smsApiProperties, skillRepository, mcpClient);
    }
}
