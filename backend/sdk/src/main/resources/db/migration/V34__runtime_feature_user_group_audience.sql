ALTER TABLE config.runtime_feature
    DROP CONSTRAINT runtime_feature_audience_mode_check;
ALTER TABLE config.runtime_feature
    ADD CONSTRAINT runtime_feature_audience_mode_check
    CHECK (audience_mode IN ('NONE', 'ALL', 'USER_GROUPS'));

-- user_group_id deliberately has no FK to iam.user_group: group hard
-- deletion (admin or SCIM) must not be blocked by feature targeting and
-- must not silently rewrite feature audiences behind the revision/history
-- protocol. A deleted group's UUID remains as a stale, fail-closed
-- target until the next explicit runtime-feature PATCH.
CREATE TABLE config.runtime_feature_audience_user_group (
    feature_name text NOT NULL
        REFERENCES config.runtime_feature(name) ON DELETE CASCADE,
    user_group_id uuid NOT NULL,
    PRIMARY KEY (feature_name, user_group_id)
);

-- One function serves triggers on two tables. plpgsql NEW/OLD are
-- runtime-typed: referencing a column the firing table lacks raises an
-- error, so the feature name must be picked per table and per operation
-- (NEW is unassigned for DELETE), never via a cross-table COALESCE.
CREATE FUNCTION config.runtime_feature_audience_targets_check()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    feature text;
    mode text;
    has_targets boolean;
BEGIN
    IF TG_TABLE_NAME = 'runtime_feature_audience_user_group' AND TG_OP = 'UPDATE' THEN
        IF OLD.feature_name IS DISTINCT FROM NEW.feature_name THEN
            RAISE EXCEPTION
                'runtime feature audience targets cannot move between features (% to %)',
                OLD.feature_name,
                NEW.feature_name;
        END IF;
    END IF;
    IF TG_TABLE_NAME = 'runtime_feature' THEN
        feature := NEW.name;
    ELSIF TG_OP = 'DELETE' THEN
        feature := OLD.feature_name;
    ELSE
        feature := NEW.feature_name;
    END IF;
    -- Serialize every check on its parent. Without this lock, deferred checks
    -- in concurrent mode and target writers can each validate a locally
    -- consistent READ COMMITTED snapshot and then commit an invalid combined
    -- state.
    SELECT audience_mode INTO mode
        FROM config.runtime_feature WHERE name = feature
        FOR NO KEY UPDATE;
    IF mode IS NULL THEN
        RETURN NULL; -- feature row deleted; cascade already cleaned targets
    END IF;
    SELECT EXISTS (
        SELECT 1 FROM config.runtime_feature_audience_user_group
        WHERE feature_name = feature
    ) INTO has_targets;
    IF mode = 'USER_GROUPS' AND NOT has_targets THEN
        RAISE EXCEPTION 'runtime feature % has USER_GROUPS audience but no targets', feature;
    END IF;
    IF mode <> 'USER_GROUPS' AND has_targets THEN
        RAISE EXCEPTION 'runtime feature % has audience targets but mode %', feature, mode;
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER runtime_feature_audience_mode_targets
    AFTER INSERT OR UPDATE ON config.runtime_feature
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION config.runtime_feature_audience_targets_check();
CREATE CONSTRAINT TRIGGER runtime_feature_audience_targets_mode
    AFTER INSERT OR UPDATE OR DELETE ON config.runtime_feature_audience_user_group
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION config.runtime_feature_audience_targets_check();
