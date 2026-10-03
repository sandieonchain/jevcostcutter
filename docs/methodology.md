# Methodology

Discover source files with a bounded no-follow walk. Detect OpenAI-compatible create calls, Anthropic-compatible messages calls, and Vercel-style generate calls. Patterns are not a complete parser: strings containing calls can cause false positives, aliases/wrappers and cross-file schemas can be missed. Generic LangGraph invoke calls are not supported yet.

Inspect bounded call arguments, prioritize generation/reasoning negative signals, and emit deterministic STATIC_CANDIDATE, KEEP_LLM or NEEDS_RUNTIME_EVIDENCE states. Explicit finite-choice, retry/stop, boolean and bounded-score language are positive signals, but are not proof of actual downstream use. Detector confidence is a heuristic score, not a calibrated probability.

IDs are content/path/line SHA-256 prefixes and change when relevant input changes. Only sanitized relative paths, line locations, fixed explanation labels and fingerprints are output. No raw source persists.

Limits: 2 MB per file; 10,000 visited file entries; approximately 50 MB decoded source budget; walk depth 64; selected Python/JavaScript/TypeScript/Java extensions only. A stopped budget scan reports truncation. Skipped counts describe entries/subtrees, not an exact count of all excluded descendant files. Reports must not imply complete inventory.

No current pricing, monetary projections, validated replacements or accuracy claims are produced. Future costs use BigDecimal, dated user-verifiable prices and overhead accounting. Future shadow reports measure agreement and coverage without changing production.

