CREATE INDEX ds_enabled_assigned_provider_created_idx
  ON profiles.data_source (assigned_instance, provider, created_at DESC)
  WHERE enabled = true AND assigned_instance IS NOT NULL;

CREATE TABLE profiles.connect_worker_run (
    worker_run_id        uuid PRIMARY KEY,
    instance_id          text NOT NULL,
    registration_id      text NOT NULL UNIQUE,
    started_at           timestamptz NOT NULL,
    last_heartbeat_at    timestamptz NOT NULL,
    heartbeat_expires_at timestamptz NOT NULL,
    heartbeat_sequence   bigint NOT NULL,
    connection_count     integer NOT NULL DEFAULT 0,
    stopped_at           timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT connect_worker_run_heartbeat_sequence_check
        CHECK (heartbeat_sequence >= 0),
    CONSTRAINT connect_worker_run_connection_count_check
        CHECK (connection_count >= 0)
);

CREATE INDEX cwr_fresh_active_idx
    ON profiles.connect_worker_run (heartbeat_expires_at DESC, connection_count, instance_id)
    WHERE stopped_at IS NULL;

CREATE INDEX cwr_instance_latest_idx
    ON profiles.connect_worker_run (instance_id, heartbeat_expires_at DESC);

CREATE TRIGGER trg_cwr_touch
    BEFORE UPDATE ON profiles.connect_worker_run
    FOR EACH ROW
    EXECUTE FUNCTION profiles.touch_updated_at();

CREATE TABLE profiles.source_runtime_observation (
    source_id              uuid NOT NULL REFERENCES profiles.data_source(id) ON DELETE CASCADE,
    observed_instance_id   text NOT NULL,
    state                  text NOT NULL,
    last_message_at        timestamptz,
    connector_started_at   timestamptz,
    observed_at            timestamptz NOT NULL,
    observation_expires_at timestamptz NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (source_id, observed_instance_id),
    CONSTRAINT source_runtime_observation_state_check
        CHECK (state IN (
            'NEW',
            'CONNECTING',
            'CONNECTED',
            'DISCONNECTED',
            'STOPPED',
            'FAILED',
            'UNKNOWN'
        ))
);

CREATE INDEX sro_fresh_source_idx
    ON profiles.source_runtime_observation (
        source_id,
        observation_expires_at DESC,
        observed_at DESC
    );

CREATE INDEX sro_instance_expiry_idx
    ON profiles.source_runtime_observation (
        observed_instance_id,
        observation_expires_at DESC
    );

CREATE INDEX sro_expiry_idx
    ON profiles.source_runtime_observation (observation_expires_at);

COMMENT ON COLUMN profiles.source_runtime_observation.connector_started_at IS
    'Runtime observation metadata only, used for best-effort UI/refetch timing and operator/debug visibility. Must not drive desired state, ownership, API success/failure semantics, or command completion.';

CREATE TRIGGER trg_sro_touch
    BEFORE UPDATE ON profiles.source_runtime_observation
    FOR EACH ROW
    EXECUTE FUNCTION profiles.touch_updated_at();

CREATE TABLE profiles.socket_source_restart (
  source_id     uuid PRIMARY KEY REFERENCES profiles.data_source(id) ON DELETE CASCADE,
  restart_epoch bigint NOT NULL DEFAULT 0,
  updated_at    timestamptz NOT NULL DEFAULT now()
);
