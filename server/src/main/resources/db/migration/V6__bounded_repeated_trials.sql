ALTER TABLE evaluation_test_results ADD COLUMN trial_index integer NOT NULL DEFAULT 1 CHECK(trial_index BETWEEN 1 AND 5);
ALTER TABLE evaluation_test_results DROP CONSTRAINT evaluation_test_results_evaluation_id_scenario_id_key;
ALTER TABLE evaluation_test_results ADD CONSTRAINT result_trial_unique UNIQUE(evaluation_id,scenario_id,trial_index);
