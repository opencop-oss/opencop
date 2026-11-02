-- Watchbox alert delivery: the outbox that carries committed detections to the
-- Redis WATCHBOX_ALERTS fan-out, the per-partition retained-retry facts that
-- sharpen detection liveness, and the retirement of the recipient-cap status
-- vocabulary. One migration because these ship as one feature.

-- ============================================================================
-- Alert delivery outbox: the durable handoff between committed detections and
-- the Redis WATCHBOX_ALERTS fan-out.
--
-- Rows are inserted in the same transaction as the detection rows they mirror, and
-- only for detections the database actually accepted (the detection insert's
-- RETURNING set), so a rolled-back batch leaves no delivery work and a replayed
-- batch creates none for its deduplicated detections. The in-process drainer claims
-- rows FOR UPDATE SKIP LOCKED, publishes, and deletes them in one transaction:
-- delete-after-publish is the retention boundary, so the steady state is an empty
-- table and a crash between publish and commit re-delivers (at-least-once).
--
-- This is a queue of live alerts, not a ledger: claimed rows whose event_time has
-- aged past the delivery freshness window are deleted without publishing (counted
-- by opencop.watchbox.alerts.expired), bounding any backlog at roughly event rate
-- times window. watchbox_detection remains the durable record of every accepted
-- detection.
CREATE TABLE watchbox.watchbox_alert_outbox (
    -- bigint identity, not uuid: the drainer claims in id order for rough FIFO.
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watchbox_id   uuid NOT NULL REFERENCES watchbox.watchbox(id) ON DELETE CASCADE,
    config_epoch  bigint NOT NULL,
    source_id     uuid NOT NULL REFERENCES profiles.data_source(id) ON DELETE CASCADE,
    -- Bounded like watchbox_detection.object_id (V37).
    object_id     varchar(255) NOT NULL,
    event_id      uuid NOT NULL,
    transition    text NOT NULL CHECK (transition IN (
        'ENTER', 'LEAVE', 'FILTER_MATCH_GAINED', 'FILTER_MATCH_LOST'
    )),
    event_time    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Each accepted detection is outboxed at most once (its identity is unique in
-- watchbox_detection and outbox rows exist only for freshly accepted detections), so
-- this index can never conflict in correct operation. It exists to turn a violation
-- of that invariant into a loud insert failure instead of a silent duplicate publish.
CREATE UNIQUE INDEX watchbox_alert_outbox_event_uniq
    ON watchbox.watchbox_alert_outbox
        (watchbox_id, config_epoch, source_id, object_id, event_id, transition);

-- ============================================================================
-- Retained retry-in-place facts on the watchbox detection heartbeat.
--
-- Listener evidence is instance-wide, so at concurrency > 1 a single wedged child stays masked
-- behind its sibling's fresh heartbeat; these columns carry the wedge onto the row so the read
-- side can degrade status while the row itself stays fresh. All four travel together (NULL means
-- no partition is wedged), and the degrade threshold is applied by the reader, not baked in here.
-- The reason vocabulary is fixed and mirrors the WatchboxDetectionHeartbeat.RetainedRetry.Reason
-- enum, whose javadoc requires this CHECK to be extended together with it.
ALTER TABLE watchbox.watchbox_detection_heartbeat
    ADD COLUMN retained_retry_partitions integer,
    ADD COLUMN retained_retry_reason     text
        CHECK (retained_retry_reason IN ('GENERATION_CHANGED', 'INFRASTRUCTURE')),
    ADD COLUMN retained_retry_since      timestamptz,
    ADD COLUMN retained_retry_attempts   bigint,
    ADD CONSTRAINT watchbox_detection_heartbeat_retained_retry_all_or_none CHECK (
        ((retained_retry_partitions IS NULL) = (retained_retry_reason IS NULL))
        AND ((retained_retry_partitions IS NULL) = (retained_retry_since IS NULL))
        AND ((retained_retry_partitions IS NULL) = (retained_retry_attempts IS NULL))
    );

-- ============================================================================
-- Retire the recipient-cap reason vocabulary from watchbox_source_status.
--
-- V32's reason_code CHECK still allows WATCHBOX_RECIPIENT_CAP_EXCEEDED and
-- WATCHBOX_AUDIENCE_UNBOUNDED, but a prior release removed those constants from the
-- WatchboxStatusReason enum without cleaning up persisted rows. The entity maps reason_code with
-- @Enumerated(EnumType.STRING), so any surviving row with a retired code at a watchbox's current
-- config epoch throws on hydration and fails the watchbox list/get endpoints for everyone. The
-- table's only readers are the epoch-scoped status finders (old-epoch rows are never hydrated),
-- so deleting the retired rows is safe; the delete spans all epochs because the vocabulary itself
-- is void and the current-epoch survivors are the failure.
--
-- The CHECK is then recreated with the live persisted vocabulary. Read-time-only reasons
-- (WATCHBOX_DETECTION_NOT_RUNNING, WATCHBOX_DETECTION_PARTIALLY_STALLED) stay deliberately
-- excluded, as V32's CHECK excluded NOT_RUNNING: they are computed from the detection heartbeat
-- during status assembly and are never persisted here.
DELETE FROM watchbox.watchbox_source_status
WHERE reason_code IN ('WATCHBOX_RECIPIENT_CAP_EXCEEDED', 'WATCHBOX_AUDIENCE_UNBOUNDED');

ALTER TABLE watchbox.watchbox_source_status
    DROP CONSTRAINT watchbox_source_status_reason_code_check;

ALTER TABLE watchbox.watchbox_source_status
    ADD CONSTRAINT watchbox_source_status_reason_code_check
        CHECK (reason_code IN (
            'WATCHBOX_DISABLED',
            'WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED',
            'WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN',
            'WATCHBOX_OBJECT_ID_CHURN_DETECTED'));
