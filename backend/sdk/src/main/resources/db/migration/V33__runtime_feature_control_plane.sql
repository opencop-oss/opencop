CREATE SCHEMA IF NOT EXISTS config;

CREATE TABLE config.runtime_feature (
    name text PRIMARY KEY,
    consumer_class text NOT NULL
        CHECK (consumer_class IN ('BACKEND_ENFORCED', 'FRONTEND_ONLY')),
    audience_mode text NOT NULL
        CHECK (audience_mode IN ('NONE', 'ALL')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE config.runtime_feature_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feature_name text NOT NULL REFERENCES config.runtime_feature(name),
    previous_audience jsonb NOT NULL,
    new_audience jsonb NOT NULL,
    resulting_revision bigint NOT NULL CHECK (resulting_revision > 0),
    changed_at timestamptz NOT NULL,
    actor_user_id uuid NOT NULL REFERENCES opencop_user(id),
    actor_email text,
    CONSTRAINT runtime_feature_history_audience_changed
        CHECK (previous_audience <> new_audience),
    CONSTRAINT runtime_feature_history_feature_revision_unique
        UNIQUE (feature_name, resulting_revision)
);

CREATE INDEX runtime_feature_history_latest_idx
    ON config.runtime_feature_history (changed_at DESC, id DESC);

INSERT INTO config.runtime_feature (name, consumer_class, audience_mode)
VALUES
    ('watchbox', 'BACKEND_ENFORCED', 'NONE'),
    ('rtsp', 'FRONTEND_ONLY', 'ALL');
