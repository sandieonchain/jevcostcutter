# Build status

## Status

**DONE — JevOpt `1.0.0-rc1` is code-complete and locally release-gated for the supported V1 scope.**

This means the repository, CLI, tests and release artifacts are ready for review and controlled use. It does **not** mean a real workload has proven that Jev should replace a production decision. That operational conclusion still requires representative non-synthetic traces and, for high-risk decisions, stronger labeled evidence or human policy.

## Goal and expected output

Goal: finish the master-spec V1 path without changing a target repository or pretending synthetic evidence is production validation.

Expected output: a Java 21 CLI that inventories bounded LLM decisions, imports minimized evidence, performs offline or explicit opt-in Jev shadow replay, reports metrics/economics, gates recommendations deterministically, emits review-only fallback-preserving patches, supports advisory analyzers, persists privacy accounting and produces reproducible audited release artifacts.

## Implemented result

- Twelve Gradle modules under neutral package `io.jevopt`.
- Read-only repository boundary, denylist, symlink/non-regular/size protections and path/secret redaction.
- Conservative deterministic call-site discovery with repository-wide eligible-source freshness invalidation.
- Strict JSON/JSONL trace v1-v3 import; trace-v3 contains only approved sanitized state, atomic question criteria, pinned `jev-1.13.0` and source-evidence fingerprint.
- SQLite schema v4 with bounded sanitized evidence, replay results and aggregate provider usage only. Raw prompts, payloads, credentials and analyzer snippets are not persisted.
- Synthetic fixture replay and explicit opt-in real Jev replay. The System One client uses named question/answer maps, a fixed HTTPS endpoint, numeric-loopback test override, redirects disabled, bounded bytes/timeouts/retries and exact model validation.
- Agreement, class coverage, confusion, abstention, confidence/probability buckets, component latency and `BigDecimal` economics. Unknown prices remain unknown.
- Deterministic recommendation gate; synthetic, stale, insufficient and high-risk agreement-only evidence cannot authorize a patch.
- Review-only supported Java patch generator that preserves the existing LLM fallback and never applies the diff.
- Advisory analyzer SPI and wired CLI providers for `none`, Codex CLI, Claude CLI and loopback Ollama. Codex/Claude use trusted executable paths, argv arrays, scrubbed environments, restricted/read-only modes, strict schemas, time/output caps and deleted temporary bundles. Ollama never auto-pulls.
- Aggregate provider accounting: invocation attempts, success/failure, request/response bytes, Jev input tokens and redaction counts; payload snippets and credentials stored: zero.
- `pricing`, `privacy-report`, `config`, `doctor`, trace, shadow, patch and reporting commands.
- Action-SHA-pinned CI, Gradle dependency verification, weekly Dependabot config, dependency-aware CycloneDX 1.5 SBOM, SHA-256 release manifest and byte-for-byte reproducibility gate.

## Verification proof — 2026-09-29

### Fresh-cache build and tests

- Java: isolated Temurin 21.0.12.1; no machine-specific path is embedded in the project.
- Gradle: wrapper 8.14.3 with strict dependency verification.
- Fresh external `GRADLE_USER_HOME` and project cache.
- Command scope: `clean build :jevopt-cli:installDist sbom checksums`.
- Result: **BUILD SUCCESSFUL; 72/72 actionable tasks executed**.
- JUnit: **92 tests, 0 failures, 0 errors, 0 skipped** across 11 XML reports.
- Every test-report hostname is rewritten to `anonymous`, including after failing tests via a finalizer.

Coverage includes hostile paths/files, secret redaction, target-tree invariance, strict traces, SQLite migration/tamper rejection, Jev Choice/Score/Noul and retry/status contracts, loopback HTTP wire tests, Ollama tags/generate contract, analyzer subprocess boundaries, stale evidence, economics, recommendation refusal/promotion boundaries and patch applicability without target writes.

### Installed CLI E2E

An installed distribution ran against the synthetic repository and fresh external state:

- `init`: succeeded outside the target.
- offline/no-LLM analysis: succeeded with zero target writes/execution/network.
- fake Codex contract executable: three static candidates analyzed through the real installed CLI wiring.
- persisted accounting: 3 invocation attempts, 289 request bytes, 303 response bytes, 0 payload snippets stored, no credential-store inspection.
- the same provider command with `--offline`: rejected with exit code 2 before invocation.
- target repository SHA-256 snapshot before/after: unchanged.

No real credential, paid request, TypeSafe endpoint, Codex service, Claude service or external Ollama service was contacted.

### Release/privacy proof

- Two clean builds used separate empty Gradle user homes and project caches.
- TAR, ZIP, SBOM and checksum manifest compared byte-for-byte: **PASS**.
- Privacy audit inspected **358** source/generated/archive items: **0 findings**.
- CycloneDX 1.5 SBOM: 19 components, including 7 third-party components with artifact SHA-256 hashes.
- Current artifacts:
  - `jevopt-1.0.0-rc1.tar`: `e578617bbfc701a34fb3efdf9c61b8d99703d201c612aa2e1197007d5dd40068`
  - `jevopt-1.0.0-rc1.zip`: `a12da747b9db72813025fea4f12d06c92e3e925b4e646d78dad31cde3bb50c62`
  - `jevopt.cdx.json`: `f97cb2a94d679c47ab7ee11a39865ac0f1d38b4dcf409425389db9f19ea4a595`

## Honest remaining conditions

These are deployment/evidence conditions, not unfinished code in the supported V1 scope:

- No live provider call was made; current official contracts must be revalidated before a public release.
- No representative production traces or labeled outcomes were supplied, so no real replacement/savings claim exists.
- Detection remains conservative source analysis, not a universal AST/dataflow engine; unsupported wrappers/frameworks may be missed.
- Patch generation supports a narrow Java direct-assignment pattern and explicitly reports compilation/tests as unrun.
- Hosted CI has not run because the repository has not been published or pushed.
- Public anonymity still requires pseudonymous account/network/publication operations outside the codebase.

Nothing was published, pushed, committed or sent to a provider during this work.
