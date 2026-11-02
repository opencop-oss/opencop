-- Resource audit history: one shared ledger for "who changed this, when, and from what to what"
-- across data sources, layers, layer groups and mapper profiles.
--
-- Flat and shared rather than one table per resource: four domains land together, and a fifth
-- later costs one short migration instead of a new table, endpoint and UI. Per-resource safety
-- comes from one named CHECK per entity type rather than from separate tables.
--
-- List partitioning was considered and rejected: no partitioned table exists in this codebase
-- today, only human actions are recorded so the volume does not need it, and the primary key
-- would have to become (entity_type, id). Revisit only if a resource reaches millions of rows
-- or needs bulk purge.
--
-- watchbox.watchbox_config_audit (V42) and config.runtime_feature_history stay where they are.
-- They cannot fold in without loss: runtime features are keyed by a text name rather than a
-- uuid, watchbox stores whole before/after snapshots rather than changes, and both carry
-- database invariants a shared table would have to drop.
--
-- This ledger records successful user actions only. Startup backfills, reconciliation,
-- plugin catalog sync, SCIM group deletion and the processor's merge finalization all change
-- audited state with no user behind them, and deliberately produce no rows. The machine-side
-- question ("has anything touched this resource") is answered by activity.resource_activity
-- from V47, which is a different feature.

CREATE SCHEMA audit;

CREATE TABLE audit.audit_history (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type          varchar(32) NOT NULL,
    -- No foreign key, deliberately: history must survive a legitimate hard delete of the
    -- resource it describes, and must never be cascade-deleted with it.
    entity_id            uuid NOT NULL,
    -- The name as it read at the time, so a later rename or deletion cannot rewrite history.
    -- Nullable rather than NOT NULL because this row shares the caller's transaction: a missing
    -- name must not fail the user's actual operation.
    entity_name          varchar(255),
    operation            varchar(32) NOT NULL,
    occurred_at          timestamptz NOT NULL,
    actor_user_id        uuid NOT NULL REFERENCES opencop_user(id),
    -- Copied independently at event time, like V42. Users are only ever soft-deleted, so the
    -- foreign key above stays satisfiable. It is deliberately RESTRICT, unlike the CASCADE on
    -- other opencop_user references: a purge of a user must anonymise these two columns and
    -- the name and email below, never delete the user's history.
    actor_full_name      varchar(255),
    actor_email          varchar(320),
    actor_global_admin   boolean NOT NULL,
    -- An allowlisted projection of what changed. Never a request body, and never the value of
    -- a secret, credential, token or key: those record that the field changed, by name only.
    change               jsonb NOT NULL,
    change_version       integer NOT NULL,

    CONSTRAINT audit_history_entity_type CHECK (
        entity_type IN ('DATA_SOURCE', 'LAYER', 'LAYER_GROUP', 'MAPPER_PROFILE')
    ),

    -- One global vocabulary, so the same kind of change is named the same way everywhere.
    CONSTRAINT audit_history_operation CHECK (
        operation IN (
            -- lifecycle. UNARCHIVE exists only for layers: nothing in the product lets a user
            -- restore an archived resource, but a layer archive whose cache eviction fails is
            -- undone after it committed, and that second state change leaves its own row.
            'CREATE', 'PATCH', 'ARCHIVE', 'UNARCHIVE', 'DELETE', 'SETUP_REPLACED',
            'CONNECT', 'DISCONNECT',
            -- relationships
            'MEMBERS_CHANGED', 'USER_GROUPS_CHANGED', 'LAYERS_CHANGED',
            'DATA_SOURCES_CHANGED', 'MAPPER_CHANGED',
            -- clone and merge
            'CLONED_TO', 'CREATED_FROM_CLONE', 'MERGED_FROM', 'MERGED_INTO',
            -- data movement
            'EXPORTED', 'IMPORT_REQUESTED'
        )
    ),

    -- Per-entity operation vocabularies. Written as one named constraint each rather than a
    -- single CASE so a violation names the resource it belongs to.
    CONSTRAINT audit_history_data_source_operations CHECK (
        entity_type <> 'DATA_SOURCE' OR operation IN (
            'CREATE', 'PATCH', 'SETUP_REPLACED', 'MAPPER_CHANGED',
            'CONNECT', 'DISCONNECT',
            'MEMBERS_CHANGED', 'LAYERS_CHANGED',
            'ARCHIVE', 'EXPORTED'
        )
    ),
    CONSTRAINT audit_history_layer_operations CHECK (
        entity_type <> 'LAYER' OR operation IN (
            'CREATE', 'PATCH', 'MEMBERS_CHANGED', 'DATA_SOURCES_CHANGED',
            'ARCHIVE', 'UNARCHIVE',
            'CLONED_TO', 'CREATED_FROM_CLONE', 'MERGED_FROM', 'MERGED_INTO',
            'IMPORT_REQUESTED', 'EXPORTED'
        )
    ),
    CONSTRAINT audit_history_layer_group_operations CHECK (
        entity_type <> 'LAYER_GROUP' OR operation IN (
            'CREATE', 'PATCH', 'DELETE',
            'MEMBERS_CHANGED', 'USER_GROUPS_CHANGED', 'LAYERS_CHANGED'
        )
    ),
    -- No ATTACHED: attaching a mapper is a change to the data source, recorded there as
    -- MAPPER_CHANGED rather than mirrored onto the mapper. Plugin-managed mappers are written by
    -- catalog reconciliation, which bypasses these use cases and so produces no rows at all.
    CONSTRAINT audit_history_mapper_profile_operations CHECK (
        entity_type <> 'MAPPER_PROFILE' OR operation IN ('CREATE', 'PATCH', 'ARCHIVE')
    ),

    CONSTRAINT audit_history_change_is_object CHECK (jsonb_typeof(change) = 'object'),
    -- A bound on one row's payload. The writer already caps delta lists at 100 ids plus an
    -- exact count; this is the backstop that keeps an unbounded projection out of the table.
    CONSTRAINT audit_history_change_maximum CHECK (octet_length(change::text) <= 65536),
    CONSTRAINT audit_history_change_version_positive CHECK (change_version >= 1)
);

-- The reader's orderings. occurred_at alone is not unique, so id breaks ties and keeps offset
-- paging from dropping or repeating a row between pages.
CREATE INDEX audit_history_entity_latest_idx
    ON audit.audit_history (entity_type, entity_id, occurred_at DESC, id DESC);

-- The unfiltered feed and time-range-only reads, which no index above leads with.
CREATE INDEX audit_history_latest_idx
    ON audit.audit_history (occurred_at DESC, id DESC);

CREATE INDEX audit_history_entity_type_latest_idx
    ON audit.audit_history (entity_type, occurred_at DESC, id DESC);

CREATE INDEX audit_history_operation_latest_idx
    ON audit.audit_history (entity_type, operation, occurred_at DESC, id DESC);

CREATE INDEX audit_history_actor_latest_idx
    ON audit.audit_history (actor_user_id, occurred_at DESC, id DESC);

-- Append-only is enforced here rather than left to the application. A maintenance job that must
-- purge or anonymise rows (retention, a user erasure request) opts in for its own transaction
-- with: SELECT set_config('audit.maintenance', 'on', true);
CREATE FUNCTION audit.reject_history_mutation()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('audit.maintenance', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'audit.audit_history is append-only (% rejected)', TG_OP
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_history_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_history
    FOR EACH ROW
    EXECUTE FUNCTION audit.reject_history_mutation();

COMMENT ON TABLE audit.audit_history IS
    'Append-only record of successful user actions on data sources, layers, layer groups and mapper profiles. Written inside the transaction that performs the change, so a row and its change commit or roll back together. Updates and deletes are rejected by trigger unless a maintenance transaction opts in; no retention policy. Records what users did through the admin surfaces, not everything that changed -- system paths deliberately write nothing.';

COMMENT ON COLUMN audit.audit_history.entity_id IS
    'The resource this row describes. Intentionally not a foreign key: history outlives the resource and must not cascade away with it.';

COMMENT ON COLUMN audit.audit_history.entity_name IS
    'The resource name as it read when the action happened, truncated to 255 code points. Nothing else caps these names today, so a truncated value carries nameTruncated in change.';

COMMENT ON COLUMN audit.audit_history.change IS
    'Versioned, allowlisted projection of the change. Secrets, credentials, tokens, key material and raw request bodies never appear here; a sensitive field records only that it changed.';

COMMENT ON COLUMN audit.audit_history.change_version IS
    'Shape version of change for this row. Bump when a projection changes incompatibly, and read older rows by their own version rather than reinterpreting them.';
