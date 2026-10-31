# TrustForge client

React/TypeScript interface for the TrustForge Spring Boot API. See [repository setup](../README.md), [API contracts](../docs/API_SPECIFICATION.md) and [actual verification](../docs/TEST_REPORT.md).

Run `npm ci`, `npm run dev`, `npm test -- --run`, `npm run lint`, `npm run build` and `npm run test:e2e` from this directory. The Vite proxy routes `/api` to localhost8080. Authentication uses a server-side Redis session and synchronizer CSRF token; browser query caches remain in memory. No API keys or authentication tokens belong in Vite variables or persistent browser storage.

The E2E pretest builds the compiled frontend. Default browser tests use labeled controlled HTTP API fixtures, including evaluation polling, replay and an actual export download. Set PLAYWRIGHT_BASE_URL to target an already running production proxy. `LIVE_BACKEND=1` enables the actual local SMTP/API flow described in the root README. Production static output contains no source maps and runs behind the included Nginx same-origin proxy.
