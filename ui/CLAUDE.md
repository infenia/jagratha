# Yukta UI - AI Guidance

Interactive web dashboard for Yukta.

## Build & Run Commands
- Run quality checks: `./gradlew :ui:check`
- Compile JTE: `./gradlew :ui:precompileJte`
- Build CSS: `./gradlew :ui:tailwind`
- Build JS: `./gradlew :ui:bundleJs`

## Key Locations
- Templates: `src/main/jte/`
- Frontend Components: `src/main/js/components/`
- Styles: `src/main/resources/static/css/input.css`

## Patterns
- **JTE + Alpine.js**: Server-side rendering for speed, Alpine.js for lightweight interactivity.
- **DAG Rendering**: Uses D3.js and ELK for automatic graph layout.
- **SSE Integration**: Alpine.js components subscribe to `/api/workflow/.../stream` for live updates.
- **Icon Strategy**: Use SVG icons inline or via fragments for optimal performance.
