# Versioned estimated economics

Implemented: strict user-supplied JSON snapshots and BigDecimal arithmetic. No provider prices are hard-coded. Included synthetic prices are invented test data, not vendor quotations.

Snapshot fields: version=1; safe id; ISO date; currency=USD; synthetic boolean; models mapping exact model labels to inputPerMillion/outputPerMillion; evaluatorInputPerMillion mapping pinned evaluator versions to input price; analyzerOverhead and shadowOverhead (USD or null). All fields required; unknown fields rejected. Prefer decimal strings to preserve input form. Nonnegative values up to one billion, max 12 fractional digits; NaN/infinite/negative/huge values fail. Null overhead means unknown, not free.

Use --pricing with shadow/combined reports. Snapshot id/date and synthetic flag are included. Current estimate = input/output token costs across active imported observations. Projected component = evaluator input cost for replayed observations + original LLM for abstentions; unreplayed observations retain the original LLM estimate. Fixture evaluation cost is synthetic, not an actual charge. Net estimated difference = current minus projected minus explicitly supplied analyzer/shadow overhead. Fixture estimate is informational; shadow overhead is separately supplied to avoid double-counting.

Missing model/evaluator rates or overheads remain null/unknown. Negative differences remain negative. Calculations cover imported samples only: no monthly extrapolation, guaranteed savings or migration validation. Tool charges, cache discounts, instrumentation overhead, production mix and end-to-end workflow effects are excluded and disclosed.
