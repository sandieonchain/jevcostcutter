# Synthetic runtime demonstration

Use observations.jsonl or observations.json with synthetic-agent. Both contain the same four events, so importing both deduplicates. decisions.jsonl is a one-event subset. shadow-fixture.json supplies synthetic fixture-v1 outputs: three agreements, one disagreement; disagreement abstains at 0.90. Noul has probability and null confidence. pricing.json is fictional.

Expected full replay: 4 samples, raw agreement 0.75, accepted agreement 1.0, abstention 0.25, imported coverage 1.0. Synthetic snapshot: current USD 0.000824; projected component USD 0.000222; fixture evaluation USD 0.000016; analyzer overhead USD 0.000100; supplied shadow overhead USD 0.000016; net estimated difference USD 0.000486. These are deterministic test values, not claims about Jev.
