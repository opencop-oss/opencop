-- Why an observed FAILED state failed, as a bounded code (ConnectorStateReason). Nullable and
-- additive: previous-release readers ignore it.
ALTER TABLE profiles.source_runtime_observation
    ADD COLUMN state_reason text;

ALTER TABLE profiles.source_runtime_observation
    ADD CONSTRAINT source_runtime_observation_state_reason_check
        CHECK (state_reason IS NULL
               OR (state = 'FAILED' AND state_reason IN ('INTEGRATION_UNAVAILABLE')));

COMMENT ON COLUMN profiles.source_runtime_observation.state_reason IS
    'Bounded, non-sensitive code explaining a FAILED state; null otherwise.';

-- A reason only qualifies FAILED. Writers from before this column upsert without naming it, so
-- the database clears it whenever a row leaves FAILED instead of leaving a stale reason beside
-- a state it no longer explains. Running before the CHECK, it also keeps such writers valid.
CREATE OR REPLACE FUNCTION profiles.clear_sro_state_reason()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.state <> 'FAILED' THEN
        NEW.state_reason := NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sro_clear_state_reason
    BEFORE INSERT OR UPDATE ON profiles.source_runtime_observation
    FOR EACH ROW
    EXECUTE FUNCTION profiles.clear_sro_state_reason();
