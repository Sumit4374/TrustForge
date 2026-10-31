ALTER TABLE test_scenarios DROP CONSTRAINT test_scenarios_category_check;
ALTER TABLE test_scenarios ADD CONSTRAINT test_scenarios_category_check CHECK(category IN ('NORMAL','PROMPT_INJECTION','EXCESSIVE_AGENCY','SECRET_EXPOSURE','INVALID_ARGUMENTS'));
ALTER TABLE test_scenarios ADD COLUMN expectations_json text;
ALTER TABLE test_scenarios ADD COLUMN fixture_json text;
ALTER TABLE evaluation_test_results ADD COLUMN evidence_json text;
ALTER TABLE evaluation_runs ADD COLUMN provenance_json text;
ALTER TABLE evaluation_runs ADD COLUMN baseline_id uuid REFERENCES evaluation_runs(id) ON DELETE SET NULL;
CREATE INDEX evaluation_baseline_idx ON evaluation_runs(baseline_id);
