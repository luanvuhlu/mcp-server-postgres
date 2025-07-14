package com.luanvv.mcp.model;

import org.springframework.ai.tool.annotation.ToolParam;

public record DatabaseConnection(
    @ToolParam(description = "Database host, default is 127.0.0.1", required = false)
    String host,
    @ToolParam(description = "Database name")
    String database,
    @ToolParam(description = "Database username")
    String username,
    @ToolParam(description = "Database password")
    String password,
    @ToolParam(description = "Database port, default is 5432", required = false)
    Integer port
) {

    public DatabaseConnection {
        // Compact constructor - validation and normalization
        if (port == null) {
            port = 5432;
        }
    }

    // Convenience constructor with default port
    public DatabaseConnection(String host, String database, String username, String password) {
        this(host, database, username, password, 5432);
    }

    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }
}
