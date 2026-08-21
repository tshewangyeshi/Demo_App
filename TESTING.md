# Testing

100% test coverage is the key to great vibe coding. Tests let you move fast, trust
your instincts, and ship with confidence — without them, vibe coding is just yolo
coding. With tests, it's a superpower.

## Backend

Java 21 / Spring Boot 4.1, JUnit 5, Maven.

```bash
cd backend
./mvnw test
```

Requires `JAVA_HOME` pointed at a JDK 21+ install — see README's "One-time local setup".

## Frontend

React 19 / TypeScript, Vite. **Vitest + React Testing Library**, added 2026-08-21.

```bash
cd frontend
npm test
```

- **Environment:** `jsdom` (configured in `vite.config.ts`'s `test` block)
- **Setup file:** `src/test/setup.ts` — loads `@testing-library/jest-dom` matchers
- **Config:** lives inside `vite.config.ts` (no separate `vitest.config.ts`), so Vitest shares the same Vite plugins/aliases as the dev server and build

### Test layers

- **Unit tests** — pure functions and business logic (`src/lib/*.test.ts`). Write one alongside any new function with a conditional, a conversion, or a rule (role → route mapping, timezone conversion, formatting).
- **Component tests** — not yet in use here; add with `@testing-library/react`'s `render`/`screen` when a component's behavior (not just its markup) needs coverage.
- **Integration/e2e** — none yet. The backend's real end-to-end coverage today is manual `curl`-based verification (see README's Status section) plus `/qa` browser passes.

### Conventions

- File naming: `<module>.test.ts` next to the module it tests (not a separate `__tests__/` tree).
- `describe`/`it` nesting, one `describe` per exported function/concern.
- Assert real behavior and real values — never `expect(x).toBeDefined()` as the only assertion.
- When a test's expected value depends on runtime/locale behavior (e.g. `Intl.DateTimeFormat` output), verify it by running the test, not by hand-guessing the string.

### Expectations going forward

- When writing a new function, write a corresponding test.
- When fixing a bug, write a regression test that encodes the exact broken condition.
- When adding error handling, write a test that triggers the error.
- When adding a conditional (if/else, switch), test both paths.
- Never commit code that makes existing tests fail.

CI: `.github/workflows/test.yml` runs `npm test` (frontend) and `./mvnw test`
(backend, against a real `postgres:18` service container — see the `backend`
job) on every push/PR.
