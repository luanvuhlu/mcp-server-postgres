package com.luanvv.mcp.model;

public record DatabaseConnection(
    String host,
    String database,
    String username,
    String password,
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
