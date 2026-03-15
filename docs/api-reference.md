# API Reference

Yukta provides a comprehensive REST API and a native MCP interface.

## 🌐 Swagger UI
When the application is running, explore the interactive documentation at:
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 📡 Key Endpoints

### Session Management
- `POST /api/config`: Initialize or update session configuration.
- `GET /api/sessions`: List all active sessions.

### File Tracking
- `POST /api/files`: Log a modified file path.

### Workflow Execution
- `POST /api/workflow/trigger`: Start the quality gate check.

### Log Access
- `GET /api/logs/{sessionId}`: List generated logs.
- `GET /api/logs/{sessionId}/{filename}`: Retrieve log content in JSON format.

## 🤖 MCP Tools
If connecting via MCP, the following tools are available:
- `configure_session`
- `log_file_change`
- `trigger_workflow`

## 📦 Unified Response Format
All REST responses follow this structure:
```json
{
  "timestamp": "ISO-8601",
  "status": 200,
  "message": "Descriptive message",
  "data": { ... }
}
```
