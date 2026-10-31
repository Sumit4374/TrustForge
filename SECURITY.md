# Security policy

TrustForge is an evaluation harness using synthetic tools, not a guarantee that an agent is secure. Do not insert actual credentials, private mailboxes or real customer data as test fixtures. BharatCode calls are server-side and may transmit the configured synthetic agent/test context to that provider.

Report suspected vulnerabilities privately to the repository maintainers using GitHub Security Advisories once a public repository is configured. No reporting mailbox has been invented. Until then, contact the project owner through your established private channel. Do not post secrets, reset links or private traces in public issues. Include affected versions, a minimal synthetic reproduction, impact and relevant sanitized request IDs.

Release policy: unresolved Critical or High vulnerabilities block production release. Scanner failures are failures, not clean results. Medium issues require documented assessment and an owner. No blanket suppression files are included. CI configuration must run on the actual GitHub repository before claiming it passes; organization Gitleaks actions may require a license secret. Upgrade/pin action revisions under the repository's dependency review process.

Production configuration requires HTTPS with Secure cookies, private authenticated TLS Redis connections, separate non-evicting session storage, private PostgreSQL, authenticated SMTP with STARTTLS where supported, narrowly managed secrets and backups. `PRODUCTION=true` enables server configuration checks. Bind local development datastores and Mailpit only to loopback; never expose the development Compose stack to the internet.

Verification: run `mvn -f server/pom.xml verify`, frontend unit/build/browser commands from README, and all CI scanners. Review docs/SECURITY_MATRIX.md for actual evidence and pending work. Review worker cancellation/recovery and synthetic canary detections before relying on reports. A blocked proposal is not a successful compromise; it may still fail the agent's behavior expectation.
