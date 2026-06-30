# API Trigger Plugin

The `API_TRIGGER` plugin allows you to initiate workflows via a REST API call.

## Features

- **Payload Mapping**: Automatically maps the incoming JSON request body to the initial message payload.
- **Synchronous/Asynchronous**: Supports both waiting for completion (for short flows) and immediate return of an `executionId`.
- **Validation**: Supports validation of the incoming payload against a schema.

## Usage

Trigger the workflow by sending a POST request to:
`/api/workflow/trigger`

With a body containing the `sessionId`, `workflowId`, and `payload`.
