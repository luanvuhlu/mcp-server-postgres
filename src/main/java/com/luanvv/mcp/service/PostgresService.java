package com.luanvv.mcp.service;

import com.luanvv.mcp.model.DatabaseConnection;
import com.luanvv.mcp.model.SelectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Slf4j
@Service
public class PostgresService {

    private static DatabaseConnection CACHE_CONNECTION = new DatabaseConnection(
        "127.0.0.1", "postgres", "postgres", "postgres", 5432
    );

    public Connection createConnection(DatabaseConnection connection) throws SQLException {
        log.debug("Creating database connection to {}:{}/{}",
            connection.host(), connection.port(), connection.database());

        var username = connection.username() != null ? connection.username() : CACHE_CONNECTION.username();
        var password = connection.password() != null ? connection.password() : CACHE_CONNECTION.password();
        var port = connection.port() != null ? connection.port() : CACHE_CONNECTION.port();
        var host = connection.host() != null ? connection.host() : CACHE_CONNECTION.host();
        var database = connection.database() != null ? connection.database() : CACHE_CONNECTION.database();

        CACHE_CONNECTION = new DatabaseConnection(host, database, username, password, port);

        log.debug("Using connection parameters: host={}, port={}, database={}, username={}",
            host, port, database, username);

        try {
            Connection conn = DriverManager.getConnection(
                CACHE_CONNECTION.getJdbcUrl(),
                CACHE_CONNECTION.username(),
                CACHE_CONNECTION.password()
            );
            log.info("Successfully connected to PostgreSQL database: {}:{}/{}", host, port, database);
            return conn;
        } catch (SQLException e) {
            log.error("Failed to connect to PostgreSQL database {}:{}/{}: {}",
                host, port, database, e.getMessage());
            throw e;
        }
    }

    @Tool(description = "List all schemas in a the database", returnDirect = true)
    public List<String> listSchemas(DatabaseConnection connection) {
        log.info("Listing schemas for database: {}", connection.database());
        List<String> schemas = new ArrayList<>();
        String sql = """
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
            ORDER BY schema_name
            """;

        try (Connection conn = createConnection(connection);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }

            log.info("Found {} schemas in database {}", schemas.size(), connection.database());
            log.debug("Schemas found: {}", schemas);

        } catch (SQLException e) {
            log.error("Error listing schemas for database {}: {}", connection.database(), e.getMessage(), e);
            throw new RuntimeException("Failed to list schemas: " + e.getMessage(), e);
        }

        return schemas;
    }

    @Tool(description = "List all tables in a schema", returnDirect = true)
    public List<Map<String, Object>> listTables(DatabaseConnection connection, String schema) {
        String targetSchema = schema != null ? schema : "public";
        log.info("Listing tables in schema '{}' for database: {}", targetSchema, connection.database());

        List<Map<String, Object>> tables = new ArrayList<>();
        String sql = """
            SELECT
                t.table_name,
                t.table_type,
                obj_description(c.oid, 'pg_class') as table_comment
            FROM information_schema.tables t
            LEFT JOIN pg_class c ON c.relname = t.table_name
            LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE t.table_schema = ?
            AND t.table_type IN ('BASE TABLE', 'VIEW')
            ORDER BY t.table_name
            """;

        try (Connection conn = createConnection(connection);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, targetSchema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("name", rs.getString("table_name"));
                    table.put("type", rs.getString("table_type"));
                    table.put("comment", rs.getString("table_comment"));
                    tables.add(table);
                }
            }

            log.info("Found {} tables in schema '{}' for database {}",
                tables.size(), targetSchema, connection.database());
            log.debug("Tables found: {}", tables.stream()
                .map(t -> t.get("name")).toList());

        } catch (SQLException e) {
            log.error("Error listing tables in schema '{}' for database {}: {}",
                targetSchema, connection.database(), e.getMessage(), e);
            throw new RuntimeException("Failed to list tables: " + e.getMessage(), e);
        }

        return tables;
    }

    @Tool(description = "Execute a read-only SQL query on a PostgresSQL database", returnDirect = true)
    public List<Map<String, Object>> executeQuery(DatabaseConnection connection, String query) {
        log.info("Executing query on database {}: {}", connection.database(),
            query.length() > 100 ? query.substring(0, 100) + "..." : query);

        // Validate that the query is read-only
        if (!isReadOnlyQuery(query)) {
            log.warn("Attempted to execute non-read-only query: {}", query);
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try (Connection conn = createConnection(connection);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Query executed successfully in {}ms, returned {} rows", executionTime, results.size());
            log.debug("First few results: {}", results.stream().limit(3).toList());

        } catch (SQLException e) {
            log.error("Error executing query on database {}: {} - Query: {}",
                connection.database(), e.getMessage(), query, e);
            throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
        }

        return results;
    }

    @Tool(description = "Select rows from a PostgresSQL table with optional conditions and ordering", returnDirect = true)
    public List<Map<String, Object>> select(DatabaseConnection connection, SelectRequest request) {
        log.info("Executing structured select on table '{}.{}' with conditions: {}",
            request.schema() != null ? request.schema() : "public",
            request.table(),
            request.conditions() != null ? request.conditions() : "none");

        // Build the SELECT query from structured parameters
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ");

        // Add schema if specified
        if (request.schema() != null && !request.schema().trim().isEmpty()) {
            queryBuilder.append(request.schema()).append(".");
        }

        // Add table name (escape it to prevent SQL injection)
        queryBuilder.append("\"").append(request.table().replace("\"", "\"\"")).append("\"");

        // Add WHERE conditions if specified
        if (request.conditions() != null && !request.conditions().trim().isEmpty()) {
            queryBuilder.append(" WHERE ").append(request.conditions());
        }

        // Add ORDER BY if specified
        if (request.orderBy() != null && !request.orderBy().trim().isEmpty()) {
            queryBuilder.append(" ORDER BY ").append(request.orderBy());
        }

        // Add LIMIT if specified
        if (request.limit() != null && request.limit() > 0) {
            queryBuilder.append(" LIMIT ").append(request.limit());
        }

        String query = queryBuilder.toString();
        log.debug("Generated SELECT query: {}", query);

        return executeQuery(connection, query);
    }

    private boolean isReadOnlyQuery(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        boolean isReadOnly = normalizedQuery.startsWith("select") ||
               normalizedQuery.startsWith("with") ||
               normalizedQuery.startsWith("show") ||
               normalizedQuery.startsWith("explain") ||
               normalizedQuery.startsWith("describe") ||
               normalizedQuery.startsWith("\\d");

        log.debug("Query validation - isReadOnly: {} for query: {}", isReadOnly,
            query.length() > 50 ? query.substring(0, 50) + "..." : query);

        return isReadOnly;
    }
}
