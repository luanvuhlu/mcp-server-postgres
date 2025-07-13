package com.luanvv.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SelectRequest(
    @JsonProperty(required = true) String table,
    String schema,
    String conditions,
    String orderBy,
    Integer limit
) {
    public SelectRequest {
        if (schema == null) {
            schema = "public"; // Default schema if not provided
        }
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("Limit must be a positive integer");
        }
    }
}