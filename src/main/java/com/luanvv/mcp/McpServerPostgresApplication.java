package com.luanvv.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class McpServerPostgresApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerPostgresApplication.class, args);
    }

}
