# Security policy

JevOpt is read-only by default. Unexpected target writes, path escape, credential disclosure, credential-store reads, unredacted secret/path output, and network transmission beyond explicitly configured boundaries are security bugs.

The current milestone has no runtime network or subprocess integrations. Target content is untrusted data. Init requires a new directory outside the target; static analysis writes nothing. Runtime import/replay/purge mutate only explicitly selected external state. Strict trace fields prohibit raw prompts/payloads; source and raw event IDs are never persisted. Categorical labels may still contain private business terms; use pseudonymous labels and review reports before sharing. Redaction is defense in depth, not perfect detection. Fixture replay is synthetic-only and cannot authorize replacement.

Scan a stable checkout. Portable Java no-follow checks reject symlink components and file replacement where detected; they are not a complete defense against a privileged attacker concurrently replacing mount points or ancestors. Hidden-directory exclusion limits coverage. Resource limits intentionally trade coverage for safety.

Do not attach actual secrets or private source when reporting vulnerabilities. Use private vulnerability reporting if enabled on the eventual hosting repository. This project has not been published and has no public security contact configured.
