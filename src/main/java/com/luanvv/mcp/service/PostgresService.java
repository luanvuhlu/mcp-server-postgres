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
        var username = connection.username() != null ? connection.username() : CACHE_CONNECTION.username();
        var password = connection.password() != null ? connection.password() : CACHE_CONNECTION.password();
        var port = connection.port() != null ? connection.port() : CACHE_CONNECTION.port();
        var host = connection.host() != null ? connection.host() : CACHE_CONNECTION.host();
        var database = connection.database() != null ? connection.database() : CACHE_CONNECTION.database();
        CACHE_CONNECTION = new DatabaseConnection(
            host, database, username, password, port
        );
        return DriverManager.getConnection(
            CACHE_CONNECTION.getJdbcUrl(),
            CACHE_CONNECTION.username(),
            CACHE_CONNECTION.password()
        );
    }

    @Tool(description = "List all schemas in a the database")
    public List<String> listSchemas(DatabaseConnection connection) {
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
        } catch (SQLException e) {
            log.error("Error listing schemas", e);
            throw new RuntimeException("Failed to list schemas: " + e.getMessage(), e);
        }

        return schemas;
    }

    @Tool(description = "List all tables in a schema")
    public List<Map<String, Object>> listTables(DatabaseConnection connection, String schema) {
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

            stmt.setString(1, schema != null ? schema : "public");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("name", rs.getString("table_name"));
                    table.put("type", rs.getString("table_type"));
                    table.put("comment", rs.getString("table_comment"));
                    tables.add(table);
                }
            }
        } catch (SQLException e) {
            log.error("Error listing tables", e);
            throw new RuntimeException("Failed to list tables: " + e.getMessage(), e);
        }

        return tables;
    }

    @Tool(description = "Execute a read-only SQL query on a PostgreSQL database")
    public List<Map<String, Object>> executeQuery(DatabaseConnection connection, String query) {
        // Validate that the query is read-only
        if (!isReadOnlyQuery(query)) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        List<Map<String, Object>> results = new ArrayList<>();

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
        } catch (SQLException e) {
            log.error("Error executing query: {}", query, e);
            throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
        }

        return results;
    }

    @Tool(description = "Select rows from a PostgreSQL table with optional conditions and ordering")
    public List<Map<String, Object>> select(DatabaseConnection connection, SelectRequest request) {
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

        return executeQuery(connection, query);
    }

    private boolean isReadOnlyQuery(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        return normalizedQuery.startsWith("select") ||
               normalizedQuery.startsWith("with") ||
               normalizedQuery.startsWith("show") ||
               normalizedQuery.startsWith("explain") ||
               normalizedQuery.startsWith("describe") ||
               normalizedQuery.startsWith("\\d");
    }
}
