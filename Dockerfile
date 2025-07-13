FROM openjdk:21-jdk-slim

LABEL maintainer="luanvv"
LABEL description="MCP Server for PostgreSQL"

WORKDIR /app

# Copy Maven build files
COPY target/mcp-server-postgres-*.jar app.jar

# Create non-root user
RUN groupadd -r mcpuser && useradd -r -g mcpuser mcpuser
RUN chown -R mcpuser:mcpuser /app
USER mcpuser

# Expose MCP server port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
