CREATE TABLE evaluation_profiles (
 id varchar(40) PRIMARY KEY, name varchar(120) NOT NULL, description varchar(600) NOT NULL
);
INSERT INTO evaluation_profiles VALUES ('controlled-email','Controlled email assistant','Synthetic prompt injection, excessive agency, disclosure and legitimate task checks.');
CREATE TABLE test_scenarios (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 title varchar(160) NOT NULL, category varchar(40) NOT NULL CHECK(category IN ('NORMAL','PROMPT_INJECTION','EXCESSIVE_AGENCY','SECRET_EXPOSURE')),
 description varchar(2000) NOT NULL, legitimate_task varchar(4000) NOT NULL, adversarial_input varchar(8000) NOT NULL,
 expected_output_contains varchar(160) NOT NULL, canary varchar(160) NOT NULL,
 approved boolean NOT NULL DEFAULT false, source varchar(30) NOT NULL, created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX scenario_project_idx ON test_scenarios(project_id,created_at DESC,id);
CREATE TABLE evaluation_runs (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 agent_version_id uuid NOT NULL REFERENCES agent_versions(id), created_by uuid NOT NULL REFERENCES users(id),
 state varchar(20) NOT NULL CHECK(state IN ('CREATED','QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','TIMED_OUT')),
 idempotency_key uuid NOT NULL, scenario_snapshot text NOT NULL, agent_snapshot text NOT NULL,
 scenario_count integer NOT NULL CHECK(scenario_count BETWEEN 1 AND 20), completed_count integer NOT NULL DEFAULT 0,
 passed_count integer NOT NULL DEFAULT 0, failed_count integer NOT NULL DEFAULT 0, inconclusive_count integer NOT NULL DEFAULT 0,
 prevented_calls integer NOT NULL DEFAULT 0, legitimate_tasks integer NOT NULL DEFAULT 0, duration_ms bigint NOT NULL DEFAULT 0,
 error varchar(300), created_at timestamptz NOT NULL DEFAULT now(), started_at timestamptz, finished_at timestamptz,
 UNIQUE(created_by,idempotency_key)
);
CREATE INDEX evaluation_queue_idx ON evaluation_runs(state,created_at,id);
CREATE INDEX evaluation_project_idx ON evaluation_runs(project_id,created_at DESC,id);
CREATE TABLE evaluation_test_results (
 id uuid PRIMARY KEY, evaluation_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 scenario_id uuid NOT NULL, title varchar(160) NOT NULL, category varchar(40) NOT NULL,
 outcome varchar(20) NOT NULL CHECK(outcome IN ('PASSED','FAILED','INCONCLUSIVE')),
 output text NOT NULL, assertions text NOT NULL,
 prevented_calls integer NOT NULL, unauthorized_attempts integer NOT NULL, secret_exposed boolean NOT NULL,
 legitimate_task_completed boolean NOT NULL, duration_ms bigint NOT NULL, error varchar(300),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(evaluation_id,scenario_id)
);
CREATE INDEX result_run_idx ON evaluation_test_results(evaluation_id,created_at,id);
CREATE TABLE execution_steps (
 id uuid PRIMARY KEY, evaluation_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 result_id uuid NOT NULL REFERENCES evaluation_test_results(id) ON DELETE CASCADE,
 step integer NOT NULL CHECK(step BETWEEN 0 AND 200), event varchar(40) NOT NULL, detail text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX trace_run_idx ON execution_steps(evaluation_id,created_at,id);
CREATE INDEX trace_result_idx ON execution_steps(result_id,step,created_at,id);
CREATE TABLE policy_violations (
 id uuid PRIMARY KEY, evaluation_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 result_id uuid NOT NULL REFERENCES evaluation_test_results(id) ON DELETE CASCADE,
 tool varchar(60) NOT NULL, reason varchar(300) NOT NULL, prevented boolean NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE evaluation_comparisons (
 id uuid PRIMARY KEY, baseline_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 candidate_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(baseline_id,candidate_id,created_by)
);
CREATE TABLE remediation_recommendations (
 id uuid PRIMARY KEY, evaluation_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 source varchar(30) NOT NULL, recommendation text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE model_daily_usage (
 actor_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, usage_date date NOT NULL,
 requests integer NOT NULL CHECK(requests >= 0), input_tokens bigint NOT NULL DEFAULT 0, output_tokens bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(actor_id,usage_date)
);
