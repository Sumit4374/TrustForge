-- Event order is explicit: transaction timestamps alone cannot order events in a result.
ALTER TABLE execution_steps ADD COLUMN sequence_no integer NOT NULL DEFAULT 0;
WITH ordered AS (
  SELECT id,(row_number() OVER (PARTITION BY result_id ORDER BY created_at,step,id)-1)::integer AS position
  FROM execution_steps
)
UPDATE execution_steps s SET sequence_no=ordered.position FROM ordered WHERE s.id=ordered.id;
ALTER TABLE execution_steps ADD CONSTRAINT execution_sequence_budget CHECK(sequence_no BETWEEN 0 AND 200);
CREATE UNIQUE INDEX trace_result_sequence ON execution_steps(result_id,sequence_no);
