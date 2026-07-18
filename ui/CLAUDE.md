# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 Infenia Private Limited

# UI Module CLAUDE.md

## Overview
React SPA frontend with Vite, Tailwind CSS v4, and Gradle integration. The `ui` module produces a static JAR containing the built React application, consumed by the `boot` module for deployment.

## Project Structure
- **`frontend/`**: Node/Vite/React project root (package.json, vite.config.ts, src/, e2e/)
- **`docs/design/`**: Design system spec (DESIGN.md) and mockups (session-list light/dark)
- **`build.gradle.kts`**: Gradle wiring for Vite build → JAR static assets

## Build & Development Commands

### Development
```bash
# Terminal 1: Vite dev server (http://localhost:5173 with HMR)
cd ui/frontend && pnpm dev

# Terminal 2: Spring Boot backend (http://localhost:8080)
./gradlew :boot:bootRun
```

### Production Build
```bash
# Build the React SPA and create JAR with static assets
./gradlew :ui:build
```

### Testing
```bash
# Unit/component tests (Vitest + React Testing Library)
./gradlew :ui:pnpmTest

# E2E tests (Playwright, requires browsers installed)
./gradlew :ui:pnpmTestE2e -PrunE2e=true
```

## Tech Stack
- **React 19**: UI framework
- **Vite 7+**: Fast dev server and production build
- **React Router 8**: Client-side routing (createBrowserRouter, no SSR)
- **Tailwind CSS 4**: CSS-first `@theme` design tokens (light + dark modes)
- **TanStack Query 5**: Server state & data fetching
- **TanStack Table 8**: Headless data table logic
- **shadcn/ui**: Copy-in React component primitives
- **TypeScript strict mode**: Type safety
- **Vitest + RTL**: Unit/component testing
- **Playwright**: E2E browser testing
- **ESLint (flat config) + Prettier**: Linting & formatting
- **pnpm**: Package manager (Gradle-managed Node version via node-conventions)

## Architecture

### Gradle Integration
- `pnpmBuild` task: Runs `pnpm run build` → produces `frontend/dist/`
- `processResources`: Copies `dist/` → `build/resources/main/static/` → included in JAR
- Static assets served by `boot` module via Spring's classpath resource handling
- SpaWebFluxConfig in boot: Fallback routing (non-existent routes → index.html for SPA)

### Frontend Architecture
- **Route-based**: `/` (session-list), `/sessions/:id`, `/control`, `/history`, etc.
- **Feature-based folders**: `src/features/sessions/{api,components,hooks,types}` for scalability
- **Shared UI**: `src/components/ui/` (shadcn primitives), `src/components/layout/` (AppHeader, AppFooter, etc.)
- **Lib**: `src/lib/apiClient.ts` (fetch wrapper), `src/lib/queryClient.ts`, `src/lib/theme.ts` (dark mode)
- **Design tokens**: `src/index.css` Tailwind v4 `@theme` block with full DESIGN.md palette

### Data Fetching
- REST: TanStack Query for session list, plugin metadata, workflow configs
- SSE (future): Custom EventSource hook for workflow status streaming (`GET /api/workflow/{sessionId}/status/{executionId}/stream`)
- API client unwraps `ApiResponse<T>` envelope, surfaces errors as typed exceptions

## Design System
- **Source**: `ui/docs/design/DESIGN.md` (canonical design tokens)
- **Mockups**: `ui/docs/design/session-list/{light,dark}/code.html` + screenshots
- **Colors**: ~40 Material Design 3 tokens (surface, on-surface, primary, secondary, error, etc.) with dark mode overrides
- **Typography**: IBM Plex Sans (UI), JetBrains Mono (code) — via Google Fonts (CDN) or self-hosted
- **Icons**: Material Symbols Outlined (self-hosted, not lucide-react for mockup pixel-match)
- **Spacing**: 4px baseline grid (xs/sm/md/lg/xl tokens)
- **Corners**: Sharp 0px everywhere (no border-radius)

## Development Workflow
1. **Frontend dev**: `cd frontend && pnpm dev` (Vite dev server auto-rebuilds, HMR enabled)
2. **Backend dev**: `./gradlew :boot:bootRun -x test` (proxies `/api/**` to Spring via vite.config.ts server.proxy)
3. **Tests**: `pnpm test` (watch mode) or `pnpm test:e2e` (Playwright against dev server)
4. **Production**: `./gradlew :ui:build` wires Vite output into JAR; `boot` serves it as static resources

## Native Image Support
- `boot` module includes GraalVM native-image configuration
- `ui` JAR static assets (dist/) are copied into classpath resources at build time
- SpaWebFluxConfig provides SPA fallback routing (resource resolving is native-image compatible)
- No dynamic reflection, no classloader resource enumeration needed

## Imports
@.claude/rules/coding-standards.md
@.claude/rules/git-workflow.md
