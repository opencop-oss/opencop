DO $$
DECLARE
    preview_watchbox_count bigint;
BEGIN
    SELECT count(*) INTO preview_watchbox_count FROM watchbox.watchbox;
    RAISE WARNING 'Resetting % unsupported preview Watchbox row(s) before audit-era constraints',
        preview_watchbox_count;
END $$;

TRUNCATE TABLE
    watchbox.watchbox_alert_outbox,
    watchbox.watchbox_detection,
    watchbox.watchbox_transition_state,
    watchbox.watchbox_source_status,
    watchbox.watchbox_layer,
    watchbox.watchbox
RESTART IDENTITY;

ALTER TABLE watchbox.watchbox
    ADD CONSTRAINT watchbox_name_maximum CHECK (char_length(name) <= 255),
    ADD CONSTRAINT watchbox_description_maximum CHECK (
        description IS NULL OR char_length(description) <= 4000
    );

CREATE FUNCTION watchbox.watchbox_layer_maximum_check() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.watchbox_id = OLD.watchbox_id THEN
        RETURN NEW;
    END IF;

    -- Lock before the FK's AFTER check so concurrent inserts for one Watchbox
    -- serialize without upgrading an already-held parent KEY SHARE lock.
    PERFORM 1
      FROM watchbox.watchbox
     WHERE id = NEW.watchbox_id
       FOR UPDATE;

    IF NOT FOUND THEN
        RETURN NEW; -- the foreign key reports the missing parent
    END IF;

    IF (SELECT count(*)
          FROM watchbox.watchbox_layer
         WHERE watchbox_id = NEW.watchbox_id) >= 100 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'watchbox_layer_maximum',
            MESSAGE = format('watchbox %s cannot reference more than 100 layers', NEW.watchbox_id);
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER watchbox_layer_maximum
    BEFORE INSERT OR UPDATE OF watchbox_id ON watchbox.watchbox_layer
    FOR EACH ROW EXECUTE FUNCTION watchbox.watchbox_layer_maximum_check();

CREATE TABLE watchbox.watchbox_config_audit (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watchbox_id          uuid NOT NULL REFERENCES watchbox.watchbox(id),
    operation            varchar(16) NOT NULL,
    before_snapshot      jsonb,
    after_snapshot       jsonb,
    reset_reasons        jsonb NOT NULL DEFAULT '[]'::jsonb,
    config_epoch_before  bigint,
    config_epoch_after   bigint,
    occurred_at          timestamptz NOT NULL,
    actor_user_id        uuid NOT NULL REFERENCES opencop_user(id),
    actor_full_name      varchar(255),
    actor_email          varchar(320),
    actor_global_admin   boolean NOT NULL,

    CONSTRAINT watchbox_config_audit_operation CHECK (
        operation IN ('CREATE', 'PATCH', 'ARCHIVE')
    ),
    CONSTRAINT watchbox_config_audit_snapshots_are_v1_objects CHECK (
        (before_snapshot IS NULL OR (
            jsonb_typeof(before_snapshot) = 'object'
            AND before_snapshot ?& ARRAY[
                'schemaVersion', 'id', 'name', 'description', 'enabled', 'direction',
                'polygon', 'filter', 'layerIds', 'configEpoch', 'createdAt', 'updatedAt'
            ]
            AND before_snapshot - ARRAY[
                'schemaVersion', 'id', 'name', 'description', 'enabled', 'direction',
                'polygon', 'filter', 'layerIds', 'configEpoch', 'createdAt', 'updatedAt'
            ] = '{}'::jsonb
            AND before_snapshot->'schemaVersion' = '1'::jsonb
            AND jsonb_typeof(before_snapshot->'id') = 'string'
            AND (before_snapshot->>'id')::uuid = watchbox_id
            AND jsonb_typeof(before_snapshot->'name') = 'string'
            AND char_length(before_snapshot->>'name') <= 255
            AND jsonb_typeof(before_snapshot->'description') IN ('string', 'null')
            AND (
                before_snapshot->'description' = 'null'::jsonb
                OR char_length(before_snapshot->>'description') <= 4000
            )
            AND jsonb_typeof(before_snapshot->'enabled') = 'boolean'
            AND jsonb_typeof(before_snapshot->'direction') = 'string'
            AND before_snapshot->>'direction' IN ('ENTER', 'LEAVE', 'BOTH')
            AND jsonb_typeof(before_snapshot->'polygon') = 'object'
            AND jsonb_typeof(before_snapshot->'filter') = 'object'
            AND jsonb_typeof(before_snapshot->'layerIds') = 'array'
            AND jsonb_array_length(before_snapshot->'layerIds') <= 100
            AND NOT jsonb_path_exists(
                before_snapshot->'layerIds', '$[*] ? (@.type() != "string")')
            AND NOT jsonb_path_exists(
                before_snapshot->'layerIds',
                '$[*] ? (!(@ like_regex "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))')
            AND jsonb_typeof(before_snapshot->'configEpoch') = 'number'
            AND (before_snapshot->>'configEpoch')::bigint
                IS NOT DISTINCT FROM config_epoch_before
            AND jsonb_typeof(before_snapshot->'createdAt') = 'string'
            AND before_snapshot->>'createdAt' ~ 'T.*Z$'
            AND (before_snapshot->>'createdAt')::timestamptz IS NOT NULL
            AND jsonb_typeof(before_snapshot->'updatedAt') = 'string'
            AND before_snapshot->>'updatedAt' ~ 'T.*Z$'
            AND (before_snapshot->>'updatedAt')::timestamptz IS NOT NULL
        ))
        AND
        (after_snapshot IS NULL OR (
            jsonb_typeof(after_snapshot) = 'object'
            AND after_snapshot ?& ARRAY[
                'schemaVersion', 'id', 'name', 'description', 'enabled', 'direction',
                'polygon', 'filter', 'layerIds', 'configEpoch', 'createdAt', 'updatedAt'
            ]
            AND after_snapshot - ARRAY[
                'schemaVersion', 'id', 'name', 'description', 'enabled', 'direction',
                'polygon', 'filter', 'layerIds', 'configEpoch', 'createdAt', 'updatedAt'
            ] = '{}'::jsonb
            AND after_snapshot->'schemaVersion' = '1'::jsonb
            AND jsonb_typeof(after_snapshot->'id') = 'string'
            AND (after_snapshot->>'id')::uuid = watchbox_id
            AND jsonb_typeof(after_snapshot->'name') = 'string'
            AND char_length(after_snapshot->>'name') <= 255
            AND jsonb_typeof(after_snapshot->'description') IN ('string', 'null')
            AND (
                after_snapshot->'description' = 'null'::jsonb
                OR char_length(after_snapshot->>'description') <= 4000
            )
            AND jsonb_typeof(after_snapshot->'enabled') = 'boolean'
            AND jsonb_typeof(after_snapshot->'direction') = 'string'
            AND after_snapshot->>'direction' IN ('ENTER', 'LEAVE', 'BOTH')
            AND jsonb_typeof(after_snapshot->'polygon') = 'object'
            AND jsonb_typeof(after_snapshot->'filter') = 'object'
            AND jsonb_typeof(after_snapshot->'layerIds') = 'array'
            AND jsonb_array_length(after_snapshot->'layerIds') <= 100
            AND NOT jsonb_path_exists(
                after_snapshot->'layerIds', '$[*] ? (@.type() != "string")')
            AND NOT jsonb_path_exists(
                after_snapshot->'layerIds',
                '$[*] ? (!(@ like_regex "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))')
            AND jsonb_typeof(after_snapshot->'configEpoch') = 'number'
            AND (after_snapshot->>'configEpoch')::bigint
                IS NOT DISTINCT FROM config_epoch_after
            AND jsonb_typeof(after_snapshot->'createdAt') = 'string'
            AND after_snapshot->>'createdAt' ~ 'T.*Z$'
            AND (after_snapshot->>'createdAt')::timestamptz IS NOT NULL
            AND jsonb_typeof(after_snapshot->'updatedAt') = 'string'
            AND after_snapshot->>'updatedAt' ~ 'T.*Z$'
            AND (after_snapshot->>'updatedAt')::timestamptz IS NOT NULL
        ))
    ),
    CONSTRAINT watchbox_config_audit_reset_reasons_array CHECK (
        jsonb_typeof(reset_reasons) = 'array'
        AND reset_reasons <@ '["POLYGON_CHANGED", "LAYER_REMOVED", "REENABLED"]'::jsonb
    ),
    CONSTRAINT watchbox_config_audit_epochs_positive CHECK (
        (config_epoch_before IS NULL OR config_epoch_before >= 1)
        AND (config_epoch_after IS NULL OR config_epoch_after >= 1)
    ),
    CONSTRAINT watchbox_config_audit_shape CHECK (
        (operation = 'CREATE'
            AND before_snapshot IS NULL
            AND after_snapshot IS NOT NULL
            AND reset_reasons = '[]'::jsonb
            AND config_epoch_before IS NULL
            AND config_epoch_after IS NOT NULL)
        OR
        (operation = 'PATCH'
            AND before_snapshot IS NOT NULL
            AND after_snapshot IS NOT NULL
            AND config_epoch_before IS NOT NULL
            AND config_epoch_after IS NOT NULL
            AND (config_epoch_after = config_epoch_before
                 OR config_epoch_after = config_epoch_before + 1))
        OR
        (operation = 'ARCHIVE'
            AND before_snapshot IS NOT NULL
            AND after_snapshot IS NULL
            AND reset_reasons = '[]'::jsonb
            AND config_epoch_before IS NOT NULL
            AND config_epoch_after IS NULL)
    )
);

CREATE INDEX watchbox_config_audit_latest_idx
    ON watchbox.watchbox_config_audit (occurred_at DESC, id DESC);

CREATE INDEX watchbox_config_audit_watchbox_latest_idx
    ON watchbox.watchbox_config_audit
        (watchbox_id, occurred_at DESC, id DESC);

CREATE INDEX watchbox_config_audit_actor_latest_idx
    ON watchbox.watchbox_config_audit
        (actor_user_id, occurred_at DESC, id DESC);

CREATE INDEX watchbox_config_audit_operation_latest_idx
    ON watchbox.watchbox_config_audit
        (operation, occurred_at DESC, id DESC);

COMMENT ON TABLE watchbox.watchbox_config_audit IS
    'Application-managed append-only history of successful Watchbox configuration actions.';

-- Runtime Feature history existed before action auditing. The branch has not
-- shipped this V42 contract, so discard only its legacy change rows and retain
-- every live definition, target, audience, revision, and timestamp.
TRUNCATE TABLE config.runtime_feature_history RESTART IDENTITY;

ALTER TABLE config.runtime_feature_history
    DROP CONSTRAINT runtime_feature_history_resulting_revision_check,
    DROP CONSTRAINT runtime_feature_history_audience_changed,
    DROP CONSTRAINT runtime_feature_history_feature_revision_unique;

ALTER TABLE config.runtime_feature_history
    RENAME COLUMN previous_audience TO before_audience;
ALTER TABLE config.runtime_feature_history
    RENAME COLUMN new_audience TO after_audience;
ALTER TABLE config.runtime_feature_history
    RENAME COLUMN resulting_revision TO revision_after;
ALTER TABLE config.runtime_feature_history
    RENAME COLUMN changed_at TO occurred_at;

ALTER TABLE config.runtime_feature_history
    ALTER COLUMN before_audience DROP NOT NULL,
    ALTER COLUMN actor_email TYPE varchar(320),
    ADD COLUMN consumer_class varchar(32) NOT NULL,
    ADD COLUMN operation varchar(16) NOT NULL,
    ADD COLUMN revision_before bigint,
    ADD COLUMN actor_full_name varchar(255),
    ADD COLUMN actor_global_admin boolean NOT NULL,
    ADD CONSTRAINT runtime_feature_history_feature_name_maximum
        CHECK (char_length(feature_name) <= 64),
    ADD CONSTRAINT runtime_feature_history_consumer_class
        CHECK (consumer_class IN ('BACKEND_ENFORCED', 'FRONTEND_ONLY')),
    ADD CONSTRAINT runtime_feature_history_operation
        CHECK (operation IN ('CREATE', 'PATCH')),
    ADD CONSTRAINT runtime_feature_history_revisions_non_negative
        CHECK (
            (revision_before IS NULL OR revision_before >= 0)
            AND revision_after >= 0
        ),
    ADD CONSTRAINT runtime_feature_history_before_audience_shape
        CHECK (
            before_audience IS NULL
            OR before_audience = '{"mode":"NONE"}'::jsonb
            OR before_audience = '{"mode":"ALL"}'::jsonb
            OR (
                jsonb_typeof(before_audience) = 'object'
                AND before_audience ?& ARRAY['mode', 'userGroupIds']
                AND before_audience - ARRAY['mode', 'userGroupIds'] = '{}'::jsonb
                AND jsonb_typeof(before_audience->'mode') = 'string'
                AND before_audience->>'mode' = 'USER_GROUPS'
                AND jsonb_typeof(before_audience->'userGroupIds') = 'array'
                AND jsonb_array_length(before_audience->'userGroupIds') BETWEEN 1 AND 100
                AND NOT jsonb_path_exists(
                    before_audience->'userGroupIds',
                    '$[*] ? (@.type() != "string")')
                AND NOT jsonb_path_exists(
                    before_audience->'userGroupIds',
                    '$[*] ? (!(@ like_regex "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))')
            )
        ),
    ADD CONSTRAINT runtime_feature_history_after_audience_shape
        CHECK (
            after_audience = '{"mode":"NONE"}'::jsonb
            OR after_audience = '{"mode":"ALL"}'::jsonb
            OR (
                jsonb_typeof(after_audience) = 'object'
                AND after_audience ?& ARRAY['mode', 'userGroupIds']
                AND after_audience - ARRAY['mode', 'userGroupIds'] = '{}'::jsonb
                AND jsonb_typeof(after_audience->'mode') = 'string'
                AND after_audience->>'mode' = 'USER_GROUPS'
                AND jsonb_typeof(after_audience->'userGroupIds') = 'array'
                AND jsonb_array_length(after_audience->'userGroupIds') BETWEEN 1 AND 100
                AND NOT jsonb_path_exists(
                    after_audience->'userGroupIds',
                    '$[*] ? (@.type() != "string")')
                AND NOT jsonb_path_exists(
                    after_audience->'userGroupIds',
                    '$[*] ? (!(@ like_regex "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))')
            )
        ),
    ADD CONSTRAINT runtime_feature_history_action_shape
        CHECK (
            (operation = 'CREATE'
                AND consumer_class = 'FRONTEND_ONLY'
                AND before_audience IS NULL
                AND after_audience = '{"mode":"NONE"}'::jsonb
                AND revision_before IS NULL
                AND revision_after = 0)
            OR
            (operation = 'PATCH'
                AND before_audience IS NOT NULL
                AND revision_before IS NOT NULL
                AND (
                    (before_audience = after_audience
                        AND revision_after = revision_before)
                    OR
                    (before_audience <> after_audience
                        AND revision_after = revision_before + 1)
                ))
        );

CREATE INDEX runtime_feature_history_feature_latest_idx
    ON config.runtime_feature_history
        (feature_name, occurred_at DESC, id DESC);

CREATE INDEX runtime_feature_history_actor_latest_idx
    ON config.runtime_feature_history
        (actor_user_id, occurred_at DESC, id DESC);

CREATE INDEX runtime_feature_history_operation_latest_idx
    ON config.runtime_feature_history
        (operation, occurred_at DESC, id DESC);

COMMENT ON TABLE config.runtime_feature_history IS
    'Application-managed append-only history of successful Runtime Feature actions.';
COMMENT ON COLUMN config.runtime_feature_history.consumer_class IS
    'Direct consumer class captured when the action occurred.';
COMMENT ON COLUMN config.runtime_feature_history.actor_full_name IS
    'Direct full-name claim captured when the action occurred; null when absent.';
COMMENT ON COLUMN config.runtime_feature_history.actor_email IS
    'Direct email claim captured when the action occurred; null when absent.';
COMMENT ON COLUMN config.runtime_feature_history.actor_global_admin IS
    'Global-admin state captured when the action occurred.';
