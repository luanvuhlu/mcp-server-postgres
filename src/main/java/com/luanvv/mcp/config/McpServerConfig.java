package com.luanvv.mcp.config;

import com.luanvv.mcp.service.PostgresService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider postgresTools(PostgresService postgresService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(postgresService)
            .build();
    }
}
