# Yukta Messaging - AI Guidance

Reactive messaging abstractions for Yukta.

## Build & Run Commands
- Run tests: `./gradlew :messaging:test`
- Run quality checks: `./gradlew :messaging:check`

## Key Locations
- Message Model: `src/main/java/com/infenia/yukta/plugin/message/DefaultMessage.java`
- SPIs: `src/main/java/com/infenia/yukta/plugin/store/MessageStore.java`
- Gateway: `src/main/java/com/infenia/yukta/plugin/gateway/MessagingGateway.java`

## Patterns
- **Immutability**: `Message` objects are immutable. Use `.withPayload()`, `.withHeader()`, etc. to create new instances.
- **Technical Headers**: Always propagate `traceId`, `timestamp`, and `priority`.
- **Reactive Interfaces**: All messaging operations return `Mono` or `Flux`.
