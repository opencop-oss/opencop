-- Event-driven resource activity: the durable write path behind `last_event_at`.
--   1. activity.recorded_activity  - each distinct happening, recorded once
--   2. activity.resource_activity  - per-resource latest activity; what reads join to
--   3. source_runtime_observation.last_message_token, and the promotion trigger
--   4. geo.features provenance, carried from decode through to persistence
--   5. backfill of data-source activity from retained runtime observations
--   6. removal of V45's per-resource last_event_at columns
--
-- Steps 1-5 and step 6 are deliberately one migration: the projection and the old columns
-- must never both exist, or the schema carries two stores for one value.
--
-- No new index is needed for the delivery fan-out: all three membership paths are already
-- covered - iam.server_layer_access has idx_sta_by_server (server_id),
-- iam.data_source_group_data_source has idx_data_source_group_data_source_data_source_id,
-- and iam.layer_data_source_group has idx_layer_data_source_group_group_id (V36).

CREATE SCHEMA activity;

-- ---------------------------------------------------------------------------
-- 1. Recorded activity
--
-- An ordinary heap table; age-based batch deletes bound retention rather than partition
-- drops. Partitioning was considered and rejected: Postgres requires a partitioned
-- table's unique index to include the partition key, which would force `effective_at`
-- into the identity below and make replay-dedup conditional on a replay carrying a
-- byte-identical time. V37__watchbox_detection.sql records the same conclusion, reached
-- from its own partitioned ancestor.
--
-- text + CHECK rather than Postgres enums, matching watchbox_detection.transition: enum
-- columns force ::text casts in every UNION read model (see LayerDirectoryEntity).
-- ---------------------------------------------------------------------------
CREATE TABLE activity.recorded_activity (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_kind  text NOT NULL CHECK (activity_kind IN (
        'SOURCE_RECEIPT', 'MAPPING_EXECUTION', 'LAYER_DELIVERY',
        'LAYER_COPY', 'LAYER_EVENT_VIEW'
    )),
    -- Writer-supplied stable identity of the happening. NOT a reference to `id`.
    -- Bounded like geo.features.object_id (V1): producer-influenced text must not reach a
    -- btree index unbounded, or an oversized value fails the insert with SQLSTATE class
    -- 54, which reads as retryable infrastructure to the caller.
    activity_id    varchar(255) NOT NULL,
    -- Polymorphic across profiles.data_source, profiles.data_mapper and iam.layer, so no
    -- foreign key is possible and nothing cascades on delete. Orphans are invisible to
    -- reads (every join is driven from the resource rows) and are swept by the same
    -- retention job as aged rows.
    resource_kind  text NOT NULL CHECK (resource_kind IN ('DATA_SOURCE', 'MAPPER', 'LAYER')),
    resource_id    uuid NOT NULL,
    -- As claimed by the writer.
    reported_at    timestamptz NOT NULL,
    -- Normalized once, on first acceptance: LEAST(reported_at, statement_timestamp()).
    -- The only column a projection ever reads. Never recomputed on replay - a conflicting
    -- insert does nothing, so a future-dated happening is clamped once and stays clamped.
    effective_at   timestamptz NOT NULL,
    -- clock_timestamp(), not now(): insert time rather than transaction start, so retries
    -- and long transactions cannot backdate when the row actually landed. Diagnostics only.
    recorded_at    timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Replay-idempotency contract: the INSERT ... ON CONFLICT DO NOTHING RETURNING target.
-- Only a returned row may advance a projection.
--
-- resource_kind/resource_id are part of the identity because one happening credits several
-- resources: a persist batch produces one MAPPING_EXECUTION row per mapper in it and one
-- LAYER_DELIVERY row per structurally visible layer, all sharing an activity_id. A
-- redelivery of that batch conflicts with every one of them.
--
-- activity_kind is part of it so the five kinds never share a key namespace - a copy
-- operation and a delivery are free to mint colliding identifiers.
CREATE UNIQUE INDEX recorded_activity_identity_uniq
    ON activity.recorded_activity (activity_kind, activity_id, resource_kind, resource_id);

-- Age-based retention deletes. The floor is the supported replay horizon, not a
-- disk-space preference: prune inside it and a replayed message no longer conflicts.
-- Kafka bounds that horizon at 7 days - ingest topics inherit the 168h broker default,
-- and DLT topics are provisioned with retention.ms = DLT_PAYLOAD_RETENTION (7d).
CREATE INDEX idx_recorded_activity_effective_at
    ON activity.recorded_activity (effective_at);

COMMENT ON TABLE activity.recorded_activity IS
    'Append-only record of distinct resource activity, one row per (kind, identity, resource). Written with ON CONFLICT DO NOTHING RETURNING; only a returned row may advance activity.resource_activity. Retention must cover the Kafka replay horizon.';

-- ---------------------------------------------------------------------------
-- 2. Resource activity projection
--
-- A row exists only once a resource has activity, so "never active" is the absent row
-- rather than a null column. That is what lets readers LEFT JOIN and return null without
-- a nullable timestamp needing a second meaning.
-- ---------------------------------------------------------------------------
CREATE TABLE activity.resource_activity (
    resource_kind text NOT NULL CHECK (resource_kind IN ('DATA_SOURCE', 'MAPPER', 'LAYER')),
    resource_id   uuid NOT NULL,
    last_event_at timestamptz NOT NULL,
    -- When the projection itself last moved, for diagnosing lag against
    -- recorded_activity. Not a domain value; never served to clients.
    updated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (resource_kind, resource_id)
);

COMMENT ON TABLE activity.resource_activity IS
    'Latest activity per resource - the durable "last used" marker the admin surfaces read. Monotonic: writes only ever move last_event_at forward. One column deliberately carries every kind of evidence (message receipt, mapping execution, layer delivery, copy, operator view), because the question it answers is "has anything touched this resource" rather than "which kind of thing".';

COMMENT ON COLUMN activity.resource_activity.last_event_at IS
    'Most recent time this resource did anything. Machine-written and never implying a user edit, which is why it lives here rather than beside created_by/updated_by on the resource row.';

-- ---------------------------------------------------------------------------
-- 3. Data-source activity, promoted from runtime observations
--
-- Connect writes source_runtime_observation every 15s for connection status
-- (ConnectInstanceRegistrar: 45s TTL, with refreshAllRuntimeStatus piggybacked on the
-- heartbeat), and BYOD writes it on its 5s coalescer flush. Hanging promotion off that
-- write keeps a durable metadata write off every per-message path, and keeps connect
-- read-only on profiles.data_source.
--
-- last_message_token is the identity of the newest accepted inbound message: connect
-- mints a new one only when onMessage accepts a message, and heartbeats carry it forward
-- unchanged. A timestamp cannot serve this role - two messages can share a millisecond,
-- and a corrected host clock can move last_message_at backwards.
-- ---------------------------------------------------------------------------
ALTER TABLE profiles.source_runtime_observation
    ADD COLUMN last_message_token varchar(255);

COMMENT ON COLUMN profiles.source_runtime_observation.last_message_token IS
    'Stable identity of the newest inbound message this observation reports. Changes only when a new message is accepted; heartbeat refreshes retain it, which is what makes an idle-but-connected source a no-op. BYOD derives it from Kafka topic, partition and offset.';

CREATE OR REPLACE FUNCTION activity.promote_source_receipt()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    accepted timestamptz;
BEGIN
    -- LEAST(..., statement_timestamp()) clamps the reporting host's clock. last_message_at
    -- comes from a connect instance, and because the projection only moves forward, one
    -- skewed host could otherwise pin a source in the future permanently. The clamp is
    -- applied here, on first acceptance, and never recomputed: a replayed token conflicts
    -- and returns nothing, so a corrected clock advances the source through a *new* token
    -- rather than by re-clamping the old one.
    INSERT INTO activity.recorded_activity (
        activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
    VALUES (
        'SOURCE_RECEIPT',
        NEW.last_message_token,
        'DATA_SOURCE',
        NEW.source_id,
        NEW.last_message_at,
        LEAST(NEW.last_message_at, statement_timestamp()))
    ON CONFLICT (activity_kind, activity_id, resource_kind, resource_id) DO NOTHING
    RETURNING effective_at INTO accepted;

    -- Nothing returned means this token was already accounted for - by an earlier
    -- heartbeat, or by another connect instance reporting the same message. It must not
    -- advance the projection a second time.
    IF accepted IS NULL THEN
        RETURN NULL;
    END IF;

    INSERT INTO activity.resource_activity AS ra (resource_kind, resource_id, last_event_at)
    VALUES ('DATA_SOURCE', NEW.source_id, accepted)
    ON CONFLICT (resource_kind, resource_id) DO UPDATE
       SET last_event_at = GREATEST(ra.last_event_at, EXCLUDED.last_event_at),
           updated_at    = clock_timestamp()
     WHERE ra.last_event_at < EXCLUDED.last_event_at;

    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION activity.promote_source_receipt() IS
    'Promotes a new inbound-message token on profiles.source_runtime_observation into durable data-source activity. Gated by its triggers'' WHEN clauses so an unchanged heartbeat never enters the body.';

-- The WHEN clauses are what make an idle heartbeat free: with N sources across M connect
-- instances re-reporting every 15s, filtering inside the function body would still cost a
-- function call per row. INSERT cannot reference OLD, hence two triggers rather than one.
CREATE TRIGGER trg_sro_promote_receipt_ins
    AFTER INSERT ON profiles.source_runtime_observation
    FOR EACH ROW
    WHEN (NEW.last_message_token IS NOT NULL AND NEW.last_message_at IS NOT NULL)
    EXECUTE FUNCTION activity.promote_source_receipt();

CREATE TRIGGER trg_sro_promote_receipt_upd
    AFTER UPDATE ON profiles.source_runtime_observation
    FOR EACH ROW
    WHEN (NEW.last_message_token IS NOT NULL
          AND NEW.last_message_at IS NOT NULL
          AND NEW.last_message_token IS DISTINCT FROM OLD.last_message_token)
    EXECUTE FUNCTION activity.promote_source_receipt();

-- ---------------------------------------------------------------------------
-- 4. Feature provenance
--
-- All nullable, and no historical rewrite: legacy rows keep null provenance, and a
-- replayed legacy row must never have new activity manufactured from its created_at.
--
-- No foreign key on (mapper_id, mapper_version) even though profiles.data_mapper is keyed
-- that way: features outlive mapper versions, and a feature must not block a mapper's
-- lifecycle. `source` already identifies the data source or layer, so no separate
-- source_id column is added.
-- ---------------------------------------------------------------------------
ALTER TABLE geo.features
    ADD COLUMN source_kind          text,
    ADD COLUMN mapper_id            uuid,
    ADD COLUMN mapper_version       int,
    ADD COLUMN ingress_id           varchar(255),
    ADD COLUMN mapping_execution_id varchar(255),
    ADD COLUMN source_received_at   timestamptz,
    ADD COLUMN mapping_completed_at timestamptz,
    -- NOT VALID: a plain ADD CONSTRAINT validates the whole table under ACCESS EXCLUSIVE,
    -- which on geo.features means locking out all reads and writes for a full scan. Every
    -- existing row has source_kind NULL and so trivially satisfies this, but Postgres will
    -- not take that on trust. NOT VALID skips the scan and still enforces the constraint
    -- on every insert and update from here on.
    ADD CONSTRAINT features_source_kind_check
        CHECK (source_kind IS NULL OR source_kind IN ('DATA_SOURCE', 'LAYER')) NOT VALID;

COMMENT ON COLUMN geo.features.ingress_id IS
    'Stable identity of the inbound message this feature came from. Preserved across ordinary transport retries; an intentional remap keeps it while taking a new mapping_execution_id.';
COMMENT ON COLUMN geo.features.source_received_at IS
    'When the inbound message was received. Distinct from created_at (commit time) and from start_at (the event''s own time): the three are different events and must not be substituted for one another.';
COMMENT ON COLUMN geo.features.mapping_completed_at IS
    'When the mapping execution that produced this feature finished.';

-- ---------------------------------------------------------------------------
-- 5. Backfill data-source activity from retained observations
--
-- Deliberately writes the projection without a recorded_activity row: historical
-- observations carry no token, so there is no identity to record, and a backfilled value
-- is a starting floor rather than a happening. Future values already retained from a
-- skewed host are clamped here, before the monotonic rule makes them permanent.
--
-- Nothing is backfilled from V45's last_event_at columns: no writer for them ever ran in
-- any environment, so every value is null.
-- ---------------------------------------------------------------------------
INSERT INTO activity.resource_activity (resource_kind, resource_id, last_event_at)
SELECT 'DATA_SOURCE',
       o.source_id,
       LEAST(MAX(o.last_message_at), statement_timestamp())
  FROM profiles.source_runtime_observation o
 WHERE o.last_message_at IS NOT NULL
 GROUP BY o.source_id
    ON CONFLICT (resource_kind, resource_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Remove the per-resource columns
--
-- V45 added these three alongside created_by/updated_at/updated_by and reserved the shape;
-- no writer for them ever ran in any environment, so there is nothing to migrate and no
-- compatibility window to hold. V45's other audit columns stay - different feature.
--
-- This also removes the reason profiles.data_source needed a narrowed updated_at trigger:
-- with no machine-written column on the row, the unconditional trg_ds_touch from
-- V1__baseline.sql is correct as it stands. A last-event write can no longer forge an edit
-- timestamp against a stale updated_by, because it no longer touches the row at all.
-- ---------------------------------------------------------------------------
ALTER TABLE iam.layer            DROP COLUMN last_event_at;
ALTER TABLE profiles.data_source DROP COLUMN last_event_at;
ALTER TABLE profiles.data_mapper DROP COLUMN last_event_at;
