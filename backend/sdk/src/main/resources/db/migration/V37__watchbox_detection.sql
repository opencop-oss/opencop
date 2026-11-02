-- Watchbox detection runtime schema and database-enforced invariants.
--   1. watchbox_transition_state    - per-object inside/outside fold state
--   2. watchbox_detection           - the immutable detection fact
--   3. active-layer and nondecreasing-config-epoch guards
--   4. watchbox_detection_heartbeat - detection liveness, one TTL row per instance

-- 1. Transition state
CREATE TABLE watchbox.watchbox_transition_state (
    watchbox_id   uuid NOT NULL REFERENCES watchbox.watchbox(id) ON DELETE CASCADE,
    config_epoch  bigint NOT NULL,
    source_id     uuid NOT NULL REFERENCES profiles.data_source(id) ON DELETE CASCADE,
    -- Bounded like geo.features.object_id (V1). Producer-influenced text must not
    -- reach a btree index unbounded: past the index-tuple limit, inserts fail with
    -- SQLSTATE class 54, which reads as retryable infrastructure to the listener.
    object_id     varchar(255) NOT NULL,
    last_inside   boolean NOT NULL,
    last_geometry_inside boolean NOT NULL,
    last_event_id uuid NOT NULL,
    last_effective_event_time timestamptz NOT NULL,
    last_observed_at timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (watchbox_id, config_epoch, source_id, object_id)
);
CREATE INDEX idx_watchbox_transition_state_last_observed_at
    ON watchbox.watchbox_transition_state (last_observed_at);

-- 2. Detections
--
-- An ordinary heap table. Detections are expected to be sparse relative to
-- observation volume (few, high-value alerts), so age-based batch deletes
-- bound retention instead of partition drops.
CREATE TABLE watchbox.watchbox_detection (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    watchbox_id   uuid NOT NULL REFERENCES watchbox.watchbox(id) ON DELETE CASCADE,
    config_epoch  bigint NOT NULL,
    source_id     uuid NOT NULL REFERENCES profiles.data_source(id) ON DELETE CASCADE,
    object_id     varchar(255) NOT NULL,
    event_id      uuid NOT NULL,
    transition    text NOT NULL CHECK (transition IN (
        'ENTER', 'LEAVE', 'FILTER_MATCH_GAINED', 'FILTER_MATCH_LOST'
    )),
    event_time    timestamptz NOT NULL,
    -- Deliberately unbounded, unlike object_id: this is display metadata that no index covers,
    -- so an overlong value cannot fail an insert, and bounding it would turn a cosmetic label
    -- into a rejected detection. Matches geo.features.display_name.
    object_display_name text,
    -- clock_timestamp(): insert time, not transaction start, so retries and
    -- long transactions cannot backdate the audit trail of when a detection
    -- became visible.
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp()
);
-- Replay-idempotency contract: the engine's INSERT ... ON CONFLICT DO NOTHING
-- target. event_id functionally determines event_time, so the key does not
-- need event_time (the partitioned ancestor of this design carried it only to
-- satisfy the partition-key-in-unique-index rule). source_id and object_id are
-- part of the key because producer-supplied event ids are only trusted to be
-- unique per (source, object): a duplicated source replaying the same payloads,
-- or a low-entropy producer, must not suppress another scope's detection. A
-- true replay carries the same source and object, so idempotency is unchanged.
CREATE UNIQUE INDEX watchbox_detection_event_uniq
    ON watchbox.watchbox_detection
        (watchbox_id, config_epoch, source_id, object_id, event_id, transition);
-- Recent-detections read path (cursor pagination), forward-provisioned.
CREATE INDEX idx_watchbox_detection_watchbox_cursor
    ON watchbox.watchbox_detection (watchbox_id, created_at, event_time, id);
-- Age-based retention deletes.
CREATE INDEX idx_watchbox_detection_event_time
    ON watchbox.watchbox_detection (event_time);

-- 3. Database-enforced lifecycle and tracking-generation guards
--
-- A live watchbox's layers must stay ACTIVE, enforced at the database.
--
-- The application-level guards - watchbox validation checks the layer is
-- ACTIVE, layer archive/merge check for active watchbox references - are racy
-- prechecks in separate transactions. The child INSERT's FK lock (FOR KEY
-- SHARE) does not conflict with the archive UPDATE's row lock (FOR NO KEY
-- UPDATE), so both sides can validate clean READ COMMITTED snapshots and
-- commit an orphaned watchbox: one that vanished from the Processor's runtime
-- snapshot (its load joins lifecycle_state = 'ACTIVE'), never writes a status
-- row, and therefore reports green forever while never detecting again.
--
-- Same pattern as V34's audience triggers: deferred constraint triggers that
-- serialize on the iam.layer row. The watchbox-side check locks the layer row
-- FOR NO KEY UPDATE, which conflicts with the lifecycle UPDATE's own row lock,
-- so whichever transaction commits second sees the first's outcome and fails.
-- There is deliberately no application precheck in front of these guards:
-- enumerating the referencing watchboxes would disclose their existence to a
-- caller with layer rights but not necessarily watchbox visibility. The
-- triggers are the enforcement layer; their migration-owned custom SQLSTATEs
-- (23U01, 23U02 — class 23, integrity violation) are translated to friendly
-- conflict responses by GlobalExceptionManager. A future watchbox un-archive
-- path (none exists today) must extend this guard with the same check in the
-- opposite direction.

CREATE FUNCTION watchbox.watchbox_layer_requires_active_layer_check() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    state iam.layer_lifecycle_state;
BEGIN
    SELECT lifecycle_state INTO state
        FROM iam.layer WHERE id = NEW.layer_id
        FOR NO KEY UPDATE;
    IF state IS NULL THEN
        RETURN NULL; -- layer row deleted; the FK already decided this write's fate
    END IF;
    IF state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'watchbox % cannot reference layer % in lifecycle state %',
            NEW.watchbox_id, NEW.layer_id, state
            USING ERRCODE = '23U01',
                  CONSTRAINT = 'watchbox_layer_requires_active_layer';
    END IF;
    RETURN NULL;
END $$;

CREATE FUNCTION iam.layer_lifecycle_blocked_by_watchboxes_check() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- The UPDATE that fired this trigger already holds the layer's row lock, so
    -- a concurrent watchbox-side check is either committed (visible here) or
    -- blocked behind us (and will see our new lifecycle state).
    IF EXISTS (
        SELECT 1
          FROM watchbox.watchbox_layer wl
          JOIN watchbox.watchbox w ON w.id = wl.watchbox_id
         WHERE wl.layer_id = NEW.id
           AND w.archived_at IS NULL) THEN
        RAISE EXCEPTION 'layer % cannot leave ACTIVE while active watchboxes reference it', NEW.id
            USING ERRCODE = '23U02',
                  CONSTRAINT = 'layer_lifecycle_blocked_by_watchboxes';
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER watchbox_layer_requires_active_layer
    AFTER INSERT OR UPDATE ON watchbox.watchbox_layer
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION watchbox.watchbox_layer_requires_active_layer_check();

CREATE CONSTRAINT TRIGGER layer_lifecycle_blocked_by_watchboxes
    AFTER UPDATE OF lifecycle_state ON iam.layer
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    WHEN (OLD.lifecycle_state = 'ACTIVE' AND NEW.lifecycle_state <> 'ACTIVE')
    EXECUTE FUNCTION iam.layer_lifecycle_blocked_by_watchboxes_check();

-- Config epochs identify tracking generations. An ordinary stale PATCH may keep
-- last-writer-wins behavior for display metadata, but it must never overwrite a
-- concurrent tracking reset with an older generation.

CREATE FUNCTION watchbox.watchbox_config_epoch_non_decreasing_check() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'watchbox_config_epoch_non_decreasing',
        MESSAGE = format(
            'watchbox %s config_epoch cannot decrease from %s to %s',
            OLD.id,
            OLD.config_epoch,
            NEW.config_epoch),
        HINT = 'Retry the watchbox change against the latest tracking generation.';
END $$;

CREATE TRIGGER watchbox_config_epoch_non_decreasing
    BEFORE UPDATE OF config_epoch ON watchbox.watchbox
    FOR EACH ROW
    WHEN (NEW.config_epoch < OLD.config_epoch)
    EXECUTE FUNCTION watchbox.watchbox_config_epoch_non_decreasing_check();

-- 4. Detection liveness heartbeat
--
-- One row per Processor instance, refreshed only while the detection listener
-- proves its poll loop is turning. Core's status rollup reads it to
-- distinguish "no bad news" from "nobody is detecting anything": a missing or
-- expired row means detection is not running - absence must never read as
-- healthy.
CREATE TABLE watchbox.watchbox_detection_heartbeat (
    instance_id          text PRIMARY KEY,
    listener_started_at  timestamptz NOT NULL,
    last_heartbeat_at    timestamptz NOT NULL,
    heartbeat_expires_at timestamptz NOT NULL,
    last_evaluated_at    timestamptz,
    assigned_partitions  integer NOT NULL DEFAULT 0,
    heartbeat_sequence   bigint NOT NULL,
    stopped_at           timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT watchbox_detection_heartbeat_sequence_check CHECK (heartbeat_sequence >= 0)
);

CREATE INDEX wdh_fresh_idx
    ON watchbox.watchbox_detection_heartbeat (heartbeat_expires_at DESC)
    WHERE stopped_at IS NULL;
