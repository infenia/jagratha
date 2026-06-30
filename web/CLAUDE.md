# Yukta Web - AI Guidance

REST API and Streaming controllers for Yukta.

## Build & Run Commands
- Run tests: `./gradlew :web:test`
- Run quality checks: `./gradlew :web:check`

## Key Locations
- Controllers: `src/main/java/com/infenia/yukta/controller/`
- Exception Mapping: `src/main/java/com/infenia/yukta/controller/advice/GlobalExceptionHandler.java`
- DTO Mappers: `src/main/java/com/infenia/yukta/mapper/`

## Patterns
- **Reactive Controllers**: Return `Mono<ResponseEntity<T>>` or `Flux<T>`.
- **SSE Streaming**: Use `MediaType.TEXT_EVENT_STREAM_VALUE` for real-time progress and logs.
- **DTO Isolation**: Map domain models to DTOs using MapStruct before returning to the client.
- **Unified Responses**: All API responses follow the `Response` wrapper structure.
