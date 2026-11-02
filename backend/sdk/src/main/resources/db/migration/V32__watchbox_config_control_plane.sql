CREATE SCHEMA IF NOT EXISTS watchbox;

CREATE TABLE watchbox.watchbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    description text,
    enabled boolean NOT NULL DEFAULT true,
    direction text NOT NULL CHECK (direction IN ('ENTER', 'LEAVE', 'BOTH')),
    polygon_json jsonb NOT NULL,
    filter_json jsonb NOT NULL DEFAULT '{"predicates":[]}'::jsonb,
    config_epoch bigint NOT NULL DEFAULT 1 CHECK (config_epoch >= 1),
    created_by uuid REFERENCES opencop_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_by uuid REFERENCES opencop_user(id),
    archived_at timestamptz,
    CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

CREATE INDEX idx_watchbox_active_created_at
    ON watchbox.watchbox (created_at DESC)
    WHERE archived_at IS NULL;

CREATE TABLE watchbox.watchbox_layer (
    watchbox_id uuid NOT NULL REFERENCES watchbox.watchbox(id) ON DELETE CASCADE,
    layer_id uuid NOT NULL REFERENCES iam.layer(id),
    PRIMARY KEY (watchbox_id, layer_id)
);

CREATE INDEX idx_watchbox_layer_layer_id
    ON watchbox.watchbox_layer (layer_id);

CREATE TABLE watchbox.watchbox_source_status (
    watchbox_id uuid NOT NULL REFERENCES watchbox.watchbox(id) ON DELETE CASCADE,
    config_epoch bigint NOT NULL,
    source_id uuid NOT NULL REFERENCES profiles.data_source(id) ON DELETE CASCADE,
    status text NOT NULL CHECK (status IN ('ACTIVE', 'DEGRADED', 'FAILED', 'DISABLED')),
    reason_code text NOT NULL CHECK (reason_code IN ('WATCHBOX_DISABLED', 'WATCHBOX_RECIPIENT_CAP_EXCEEDED', 'WATCHBOX_AUDIENCE_UNBOUNDED', 'WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED', 'WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN', 'WATCHBOX_OBJECT_ID_CHURN_DETECTED')),
    since_at timestamptz NOT NULL DEFAULT now(),
    observed_count bigint CHECK (observed_count IS NULL OR observed_count >= 0),
    cap bigint CHECK (cap IS NULL OR cap >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (watchbox_id, config_epoch, source_id)
);

CREATE INDEX idx_watchbox_source_status_watchbox_epoch
    ON watchbox.watchbox_source_status (watchbox_id, config_epoch);
