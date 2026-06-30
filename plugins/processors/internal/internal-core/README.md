# Internal Core Processors

This module contains the fundamental EIP (Enterprise Integration Pattern) processors that form the building blocks of Yukta workflows.

## Processors

### Router Patterns
- **BRANCH**: Routes messages based on SpEL expressions.
- **SPLITTER**: Breaks a single message into multiple messages.
- **AGGREGATOR**: Combines multiple messages into one.
- **RESEQUENCER**: Ensures messages are processed in a specific order.

### Transformer Patterns
- **MAPPER**: Transforms the message payload using expressions.
- **ENRICHER**: Adds data to a message from an external source.
- **CONTENT_FILTER**: Removes unnecessary data from a message.
- **CLAIM_CHECK**: Temporarily stores and retrieves message payloads.

### Filter Patterns
- **FILTER**: Drops messages that do not meet a certain criteria.

### Flow Patterns
- **LOOP**: Repeats a section of the workflow.
- **SUB_WORKFLOW**: Executes another workflow as a single step.
- **FAILURE_STRATEGY**: Defines how to handle errors in a workflow segment.

## Shared Utilities
Includes `MergeUtils`, `MapMessageMapper`, and `SimpleExpressionEvaluator` for consistent behavior across all internal processors.
