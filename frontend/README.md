# Personal Finance — Frontend

Angular 17 SPA for the Personal Finance app. Standalone components, Angular Material UI, proxy to Spring Boot backend.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Angular 17 (standalone components) |
| UI Library | Angular Material (indigo-pink theme) |
| Styles | SCSS |
| HTTP | Angular HttpClient + proxy to backend |
| Linting | ESLint 8 + @angular-eslint/17 + @typescript-eslint/7 |
| Formatting | Prettier 3 |
| Tests | Karma + Jasmine |
| Build | Angular CLI 17 |

## Project Structure

```
src/app/
├── app.config.ts      # Root providers (Router, AnimationsAsync)
├── app.routes.ts      # Lazy-loaded route definitions
└── app.component.*    # Root shell component

feature/               # (planned — see roadmap below)
├── auth/              # Login, Register
├── import/            # PDF upload + preview
├── review/            # Unknown merchant review queue
├── dashboard/         # Metrics + 50/30/20 breakdown
├── transactions/      # Transaction list + manual entry
├── categories/        # Category management
└── known-persons/     # Known PIX persons management
```

## Prerequisites

- Node.js 20+
- npm 9+

## Running Locally

**1. Install dependencies:**
```bash
npm install
```

**2. Start the dev server:**
```bash
npm start
```

Opens at `http://localhost:4200`. API calls to `/api/*` are proxied to the backend at `http://localhost:8080` via `proxy.conf.json`.

> The backend must be running locally for API calls to work. See [`../backend/README.md`](../backend/README.md).

## Available Scripts

```bash
# Dev server (proxied to :8080)
npm start

# Run unit tests (Karma/Jasmine, watch mode)
npm test

# Run unit tests once (CI mode)
npm test -- --watch=false --browsers=ChromeHeadless

# Run a specific spec file
npx ng test --include='**/app.component.spec.ts'

# Lint
npm run lint

# Check formatting
npm run format:check

# Apply formatting
npm run format

# Production build
npm run build
```

## Proxy Configuration

In development, Angular's dev server forwards `/api/*` requests to `http://localhost:8080` via [`proxy.conf.json`](proxy.conf.json). No CORS issues in development.

In production (Docker), Nginx forwards `/api/` to `http://backend:8080/api/`.

## Code Style

- **ESLint**: `@angular-eslint/recommended` + `@typescript-eslint/recommended`
  - Components: `app-*` prefix, kebab-case selectors
  - Directives: `app*` prefix, camelCase attribute selectors
  - No unused vars (except `_` prefixed params)
- **Prettier**: single quotes, semicolons, 100-char print width, LF line endings

Both are enforced in CI. Run `npm run lint` and `npm run format:check` before pushing.

## Adding a New Feature Module

1. Create `src/app/feature/<name>/` directory
2. Add standalone components with `app-<name>` selector prefix
3. Create `<name>.routes.ts` with lazy-loaded routes
4. Create `<name>.service.ts` for HTTP calls
5. Register in `app.routes.ts` with `loadChildren`

## Roadmap

| Checkpoint | Feature | Status |
|---|---|---|
| 7 | Auth (Login, Register, Guard, Interceptor) | Planned |
| 7 | PDF Import (Upload + Preview table) | Planned |
| 7 | Review Queue | Planned |
| 8 | Dashboard (50/30/20, destaques) | Planned |
| 8 | Transaction List | Planned |
| 8 | Categories | Planned |
| 8 | Known Persons | Planned |
| 8 | Layout (Sidenav + toolbar) | Planned |
