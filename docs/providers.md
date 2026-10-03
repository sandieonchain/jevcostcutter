# Analyzer providers

Implemented providers are `none`, official Codex CLI, official Claude CLI and loopback Ollama. Analyzer results are advisory only and can never authorize a recommendation or patch.

- Codex requires a trusted executable, exact model, explicit consent and an explicit `CODEX_HOME` environment reference. It uses `exec`, ephemeral mode, ignored user config/rules, read-only sandbox, strict output schema and no shell concatenation.
- Claude requires a trusted executable, exact model, explicit consent and `ANTHROPIC_API_KEY` by name. It uses bare/restricted print mode, no tools, strict MCP configuration, no session persistence and strict JSON schema.
- Ollama accepts only an exact loopback HTTP origin, checks that the pinned model is already installed, never pulls a model, disables redirects and bounds responses.
- `none` performs no process or network operation.

Every invocation needs external initialized state so JevOpt can persist aggregate attempts, success/failure, request/response byte counts and redaction counts without storing source snippets, prompts, tokens or credentials. Installed CLI flags were verified against Codex CLI 0.156.0 and Claude Code 2.1.261 on 2026-09-29; adapters must be revalidated before later releases.
