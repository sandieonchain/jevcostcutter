CREATE TABLE provider_usage (
  provider TEXT NOT NULL,
  provider_version TEXT NOT NULL,
  invocations INTEGER NOT NULL,
  successes INTEGER NOT NULL,
  failures INTEGER NOT NULL,
  request_bytes INTEGER NOT NULL,
  response_bytes INTEGER NOT NULL,
  input_tokens INTEGER NOT NULL,
  redactions INTEGER NOT NULL,
  PRIMARY KEY (provider, provider_version)
);
UPDATE metadata SET value='4' WHERE name='schema_version';
