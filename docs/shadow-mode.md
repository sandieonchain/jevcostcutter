# Shadow evaluation

The `ShadowEvaluator` SPI supports synthetic `fixture-v1` replay and explicit opt-in real Jev replay pinned to `jev-1.13.0`. Production decisions are never changed. Fixture evidence remains synthetic and can never authorize a patch. Real replay accepts only non-synthetic trace-v3 records with approved sanitized state, atomic question criteria and a matching source-evidence fingerprint.

```sh
jevopt init --repo examples/synthetic-agent --state-dir ../jevopt-demo-state
jevopt traces import examples/synthetic-traces/observations.jsonl --repo examples/synthetic-agent --state-dir ../jevopt-demo-state
jevopt shadow start --fixtures examples/synthetic-traces/shadow-fixture.json --threshold 0.90 --repo examples/synthetic-agent --state-dir ../jevopt-demo-state
jevopt shadow report --repo examples/synthetic-agent --state-dir ../jevopt-demo-state --pricing examples/synthetic-traces/pricing.json --format json
jevopt report --repo examples/synthetic-agent --state-dir ../jevopt-demo-state
```

The external state directory must not exist before init. Use the built CLI path or place it on PATH. Explicit init/import/replay/purge mutate only selected external state.

Fixture schema: object with version=1, evaluatorVersion=fixture-v1, synthetic=true and results array. Each result has candidateId, eventId, selectedValue, probabilities (exact option keys, summing to one within 0.000001), confidence, inputTokens and latencyMs. Choice/Score require confidence in [0,1]. Noul requires confidence=null and uses selected-decision probability for abstention; there is no separate Noul confidence. Threshold range [0,1], max six decimal places; equality is accepted. Unknown/stale/real samples, duplicate results, moving versions and malformed distributions fail atomically, preserving prior replay.

Latest replay is reported; earlier replays remain until purge. Partial fixture coverage is allowed and measured. Reports include active/stale counts, raw/accepted agreement, abstention, imported-workload coverage, per-candidate/per-class agreement, observed-option coverage, confusion matrices, confidence buckets, separate Noul probability buckets and nearest-rank component latency p50/p95. Bucket ranges: [0,0.5), [0.5,0.9), [0.9,1]. Unpopulated rates are null, not zero or perfect. Metrics use current eligible candidates; new imports reduce coverage and changed source invalidates affected evidence.

Agreement is not accuracy without labels. Fixture numbers are synthetic, not measured Jev performance. Real replay still requires representative workload coverage and, for high-risk decisions, stronger labeled evidence or human policy. Every outbound replay needs explicit `--jev --allow-remote`, a named environment variable and the pinned model; offline mode rejects before transport.
