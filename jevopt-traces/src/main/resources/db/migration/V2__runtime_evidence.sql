CREATE TABLE samples (fingerprint TEXT PRIMARY KEY, candidate_id TEXT NOT NULL, data TEXT NOT NULL);
CREATE TABLE replay_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, evaluator_version TEXT NOT NULL, threshold TEXT NOT NULL, imported_count INTEGER NOT NULL);
CREATE TABLE shadow_results (run_id INTEGER NOT NULL REFERENCES replay_runs(id) ON DELETE CASCADE, fingerprint TEXT NOT NULL REFERENCES samples(fingerprint), data TEXT NOT NULL, PRIMARY KEY(run_id,fingerprint));
UPDATE metadata SET value='2' WHERE name='schema_version';
