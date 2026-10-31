# TrustForge

**Build AI agents. Break them safely. Make them trustworthy.**

TrustForge evaluates model-backed agent behavior in a controlled synthetic environment. Configure a versioned agent, generate or write scenarios, review them, execute bounded workflows, inspect deterministic evidence, refine permissions and compare runs under the same saved conditions.

The platform distinguishes an agent's attempted violation from a policy control preventing execution. A blocked action can still fail the behavior test. Provider errors and insufficient evidence are inconclusive. AI recommendations never replace measured assertions or silently change controls.

Phase2 adds reviewed correctness oracles, versioned provenance, a document-retrieval environment, bounded repeated trials, evidence-based benchmarks and explicit proposal approval. **Release decision: BLOCKED pending real BharatCode verification.** Local checks pass:87 backend,16 frontend unit,7 Chromium and2 Firefox/WebKit tests; scans have0 High/Critical findings. See [completion report](docs/IMPROVEMENT_COMPLETION_REPORT.md).

## What is implemented

- Registration, real SMTP email verification/reset, Argon2id passwords, CSRF-protected Redis sessions, login rotation, logout, expiry, password changes, editable display name, active-session review/revocation and verification resend.
- Projects with OWNER/EDITOR/VIEWER membership, server-side access checks, immutable agent versions and synthetic tool policies. Platform admins have operational aggregates without a project-data bypass.
- Manual and BharatCode-generated scenarios, explicit human approval, PostgreSQL-backed bounded evaluation queue, cancellation, restart recovery, idempotent submission and quotas.
- Multi-step model decisions with strict JSON validation; deterministic simulated email, document, database, calendar and restricted-record policies.
- Category-specific normal-task, prompt-injection, excessive-agency, invalid-argument and synthetic-disclosure assertions with reviewed exact action/resource/label oracles, redacted chronological traces, report export, supplementary remediation, saved-scenario replay and measured comparisons.
- Responsive dark React UI with protected deep links, in-memory query caching, actual dashboard aggregates, graphs/charts and meaningful empty/error states.
- Separate session/cache Redis instances, scoped dashboard caching, Flyway migrations, safe structured audit/logging, metrics, Docker infrastructure, startup scripts and CI configuration.

[Actual dashboard screenshot](docs/screenshots/dashboard.png) · [Mobile scenario screenshot](docs/screenshots/scenarios-mobile.png)

Live BharatCode inference requires your server-side key. **No live key was supplied during implementation; live provider generation, inference and remediation remain unverified.** Test-only fake providers are confined to test sources and clearly labeled. No production fixture fallback or sample evaluation statistics are included. See [actual test results](docs/TEST_REPORT.md) and [release limitations](docs/FINAL_IMPLEMENTATION_REPORT.md).

## Stack and layout

Java 21 bytecode, Spring Boot 4.1.2, Spring Security/Data JPA/Session/Redis, PostgreSQL 17, Flyway, Micrometer/Actuator, Java HttpClient; React 19, TypeScript 7, Vite 8, React Router, TanStack Query, React Hook Form/Zod, Tailwind, accessible native controls, React Flow and Recharts. Maven manages JUnit Jupiter (Boot 4's managed Jupiter version), Mockito and Testcontainers. Browser testing uses Vitest/Testing Library and Playwright.

```text
server/             Existing Spring Boot application; modular domain packages
client/             Existing Vite React application
fixtures/           Synthetic draft agent/scenario definitions, never result data
scripts/            SMTP smoke test, demo authoring, OpenAPI generation
compose.yaml        Loopback-only PostgreSQL, isolated Redis instances, Mailpit
start.sh / stop.sh  Local lifecycle scripts
.github/workflows/  Build, tests, dependency/secret/container checks and SBOM
BharatCode.txt      Provider integration and hackathon notes
docs/               Plan, architecture, API, threats, verification and decisions
```

## Local setup

Requires a supported JDK 21 or later LTS, Maven (or the existing wrapper), Node 24 LTS or compatible later runtime, npm, Docker with Compose, Python 3, and Linux `setsid`. Development was also exercised on the provided OpenJDK 27/Node 26 host; deployment images/CI target Java 21/Node 24. The original `server/` and `client/` were repaired in place.

```sh
./start.sh
```

On first use, the script creates ignored `.env` with random local datastore passwords and mode 0600. It validates tools/configuration/ports, starts infrastructure and waits for health, starts Spring (Flyway applies/validates migrations before readiness), starts Vite and prints URLs. Ctrl+C stops its child process groups and supporting services while retaining database/session volumes. `./stop.sh` stops supporting services separately. Application logs are in `/tmp/trustforge-backend.log` and `/tmp/trustforge-frontend.log`.

Default URLs: application http://localhost:5173; API http://localhost:8080; Mailpit http://localhost:8025. Database/Redis/SMTP ports bind only to 127.0.0.1. If conventional ports are occupied, set `POSTGRES_PORT`, `REDIS_PORT`, `CACHE_REDIS_PORT`, `SMTP_PORT`, `MAILPIT_HTTP_PORT` in `.env` and update `DB_URL` accordingly.

In this restricted workspace, the normal Maven home cache is read-only. Use:

```sh
TRUSTFORGE_MAVEN_REPO="$PWD/.run/maven-repository" ./start.sh
```

This workspace's ignored `.env` uses PostgreSQL 55432, session Redis 56379, cache Redis 56380, SMTP 51025 and Mailpit http://localhost:58025 to avoid existing services. These are local configuration values, not checked-in credentials.

### Environment

Copy [.env.example](.env.example) if configuring manually. Required datastore passwords have no hardcoded defaults. Important settings:

| Setting | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL JDBC connection |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_TLS` | Non-evicting session/rate-limit Redis |
| `CACHE_REDIS_HOST`, `CACHE_REDIS_PORT`, `CACHE_REDIS_PASSWORD`, `CACHE_REDIS_TLS` | Separate evictable data cache |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS` | Real SMTP delivery; SMTP_TLS requires STARTTLS; Mailpit is local-only |
| `APP_BASE_URL` | Trusted frontend origin used in verification/reset links |
| `APP_SECURE_COOKIES` | `false` for local HTTP; **true in production** |
| `PRODUCTION` | Enable fail-safe production checks |
| `BHARATCODE_API_KEY` | Server-only live inference key |
| `BHARATCODE_BASE_URL` | Documented fixed HTTPS BharatCode endpoint |
| `BHARATCODE_MODEL` | Default `qwen-3.8-27b`; confirm account access with provider |
| `BHARATCODE_TIMEOUT` | Per-request seconds, bounded by scenario deadline |
| `BHARATCODE_DAILY_REQUEST_LIMIT` | Per-user daily model request cap, default 200 |

Never place keys in `VITE_` variables, source files, browser storage or startup scripts. Missing/unavailable SMTP does not pretend to deliver. Missing provider credentials return an explicit unavailable response. The browser never connects to Redis or BharatCode.

### First evaluation

Register → verify using Mailpit → sign in → create project → configure agent → write/generate scenarios → review and approve → select agent version/scenarios → start → inspect evidence → select a failed trial → generate a configuration proposal → inspect its diff/permission warnings → explicitly approve a new immutable version → replay → compare → export. The [demonstration guide](docs/DEMO.md) includes four synthetic fixtures and actual-result interpretation. `python3 scripts/seed_demo.py` creates draft configuration/scenarios through the real API; it never creates results or auto-approves tests.

## Verification

Docker must be available for required backend integration tests; they do not silently skip when it is unavailable.

```sh
mvn -f server/pom.xml -B -ntp verify
npm --prefix client ci
npm --prefix client test -- --run
npm --prefix client run lint
npm --prefix client run build
npm --prefix client exec -- playwright install chromium
npm --prefix client run test:e2e
```

For this host's Docker 29 compatibility and writable dependency cache, use `mvn -Dmaven.repo.local="$PWD/.run/maven-repository" -Dapi.version=1.44 -f server/pom.xml -B -ntp verify`. A Maven/Java HTTP classpath fixture is not a substitute for a deployment build; CI additionally targets Java 21.

Default browser tests use explicitly controlled API responses to exercise routing/rendering/security errors and evaluation/replay/export without external credentials. The pretest builds the compiled frontend; set PLAYWRIGHT_BASE_URL to test a running production proxy. The optional real SMTP/backend browser test requires running infrastructure/API:

```sh
LIVE_BACKEND=1 SMOKE_MAIL_URL=http://localhost:58025 npm --prefix client run test:e2e
SMOKE_MAIL_URL=http://localhost:58025 python3 scripts/smoke.py
```

The HTTP smoke script creates two fresh synthetic accounts and checks permissions, CSRF, cache revocation and explicit provider-unavailable handling. Registration is throttled (five per IP per 15 minutes), so repeated development smoke runs may correctly return 429. Testcontainers suites use isolated fresh stores. Tests never use real private user data.

Final local runtime image and resolved test-classpath scans reported 0 High/Critical advisories and 0 detected secrets; [scan identities and summaries](docs/verification/security-scans.json) and [runtime SBOMs](docs/verification/) are included. Security scans configured in CI block on High/Critical findings and scanner failures; [SECURITY.md](SECURITY.md) describes the release policy. CI workflows are configuration until executed on an actual GitHub repository; no CI badge is fabricated.

## Deployment and security

Build `server/Dockerfile` and `client/Dockerfile`. The Nginx SPA image proxies `/api/` to `server:8080`, supports deep-link refresh, refuses dotfiles/source maps/backups and denies Actuator externally. Deploy behind an HTTPS ingress that sets HSTS; use private managed stores, authenticated TLS Redis, valid SMTP TLS, environment secrets and backups. Set `PRODUCTION=true`, `APP_SECURE_COOKIES=true` and an HTTPS `APP_BASE_URL`. Do not deploy the local Compose mail/store ports publicly. Application startup disables debug/trace; run direct jars with `--debug=false --trace=false` too.

Use exactly one worker-owning API instance for this release. PostgreSQL claiming uses transactional row locks and a bounded two-worker executor; startup marks interrupted RUNNING jobs failed with an auditable explanation while queued jobs remain durable. Horizontal multi-instance recovery leases are future work, not claimed support. Cancellation prevents subsequent tool execution and checks state after inference; already-sent provider requests may finish within their bounded deadline.

[Architecture](docs/ARCHITECTURE.md), [threat model](docs/SECURITY_THREAT_MODEL.md), [security matrix](docs/SECURITY_MATRIX.md), [API specification](docs/API_SPECIFICATION.md), [implementation plan](docs/IMPLEMENTATION_PLAN.md), [decisions](docs/DECISIONS/) and [final engineering report](docs/FINAL_IMPLEMENTATION_REPORT.md) document evidence and limits. Security/indexing headers are defense in depth; all private data remains protected even when a URL is known.

## Remaining limitations and roadmap

- Live provider verification and model-dependent outcomes require a real account/key. No security guarantee follows from a passing synthetic test.
- Only synthetic tool integrations are supported. No arbitrary shell/SQL/filesystem/network or external agent URLs are accepted.
- Trace events become visible at scenario persistence boundaries; the UI polls rather than using SSE. Exports are JSON/Markdown, not PDF.
- Replay records versioned scenario/fixture/provider/tool/policy/assertion/software/runtime provenance. Incompatible or incomplete trial evidence suppresses deltas; incompatible execution definitions cannot be replayed under old version stamps. Real model inference remains nondeterministic.
- Account deletion, email-address changes, MFA and broad operations dashboards are future work. Profile-name editing, password changes, active-session review/revocation and limited ADMIN health are implemented.
- Deployment TLS/ACL/backups, live provider and production load verification remain external release checks. See the final report for actual scanner/build/test status.

Administrators are provisioned by a trusted database operator, never public signup. After independently verifying the operator account, an authorized database administrator can set its platform role and increment `session_generation` to revoke old sessions; role changes do not bypass project membership. No default administrator password is shipped.


### Phase2 verification and evidence

After Java21 Maven verify and frontend build, run `PLAYWRIGHT_BROWSERS_PATH="$PWD/.run/playwright" python3 scripts/run_fullstack.py` (install matching Chromium first). This starts an isolated real Spring/PostgreSQL/two Redis/SMTP/Nginx stack with explicitly labeled test-classpath inference; the release JAR excludes it. `--serve` keeps that disposable target on8180 for `python3 scripts/check_production_http.py` and authorized passive `python3 scripts/check_zap.py`. See the completion report for exact executed commands and external blockers.

Live verification is opt-in: supply BHARATCODE_API_KEY securely in the server environment, then run `TRUSTFORGE_LIVE_BHARATCODE=true scripts/check_live_provider.sh`. Without both the flag and key it exits2 BLOCKED, never substituting controlled responses. [Benchmark definitions](docs/BENCHMARK_DEFINITIONS.md) document exact denominators, null empty samples, provider metadata coverage and repeated-trial limitations. [Deployment operations](deploy/OPERATIONS.md) covers singleton worker ownership, backup/restore and target-environment checks.
