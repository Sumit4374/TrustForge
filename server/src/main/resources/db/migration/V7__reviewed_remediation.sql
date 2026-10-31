CREATE TABLE remediation_proposals (
 id uuid PRIMARY KEY,
 evaluation_id uuid NOT NULL REFERENCES evaluation_runs(id) ON DELETE CASCADE,
 result_id uuid NOT NULL REFERENCES evaluation_test_results(id) ON DELETE CASCADE,
 base_version_id uuid NOT NULL REFERENCES agent_versions(id),
 proposed_config text NOT NULL,
 explanation varchar(2000) NOT NULL,
 source varchar(30) NOT NULL,
 state varchar(10) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','APPROVED','REJECTED')),
 created_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 reviewed_by uuid REFERENCES users(id),
 reviewed_at timestamptz,
 new_version_id uuid REFERENCES agent_versions(id),
 CHECK ((state='APPROVED')=(new_version_id IS NOT NULL))
);
CREATE INDEX remediation_proposals_evaluation ON remediation_proposals(evaluation_id,created_at DESC);
