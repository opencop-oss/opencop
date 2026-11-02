CREATE SCHEMA IF NOT EXISTS app;

CREATE TABLE app.export_artifact (
    export_id    UUID PRIMARY KEY,
    subject_type TEXT        NOT NULL,
    subject_id   UUID        NOT NULL,
    creator_id   UUID        NOT NULL,
    format       TEXT        NOT NULL,
    blob_path    TEXT        NOT NULL,
    filename     TEXT        NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_export_artifact_creator_id
    ON app.export_artifact (creator_id);

CREATE INDEX idx_export_artifact_subject
    ON app.export_artifact (subject_type, subject_id);

CREATE INDEX idx_export_artifact_expires_at
    ON app.export_artifact (expires_at);
