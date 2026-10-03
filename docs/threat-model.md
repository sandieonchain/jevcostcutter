# Threat model

Assets: source, prompts, traces, credentials, identity, target integrity. Trust boundary: only the Java scanner authorizes file reads; only the explicit init command can create external state. Repository content cannot change policy.

See the threat-to-control mapping in [architecture](architecture.md). Current controls cover traversal, symlinks, sensitive names, non-regular files, file/scan limits, inert prompt injection, metadata-only reports and generic CLI errors. No remote provider, execution or patch application exists. Future adapters need independent request-redaction, endpoint allowlists, environment scrubbing, output bounds and contract tests before enabling them.

Residual risks: heuristic detector errors; identifiers in otherwise ordinary filenames; high-entropy detection false positives/negatives; concurrent filesystem races; supply-chain dependencies. Dependency checksums provide integrity after trust bootstrap, not proof that dependencies are vulnerability-free.

