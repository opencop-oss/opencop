ALTER TABLE iam.layer
    ADD COLUMN archived_at timestamptz,
    ADD COLUMN archived_by uuid;

ALTER TABLE profiles.data_source
    ADD COLUMN archived_at timestamptz,
    ADD COLUMN archived_by uuid;

ALTER TABLE iam.layer
    DROP CONSTRAINT IF EXISTS layer_name_key;

ALTER TABLE profiles.data_source
    DROP CONSTRAINT IF EXISTS data_source_name_key;

CREATE UNIQUE INDEX uk_layer_name_active
    ON iam.layer (name)
    WHERE archived_at IS NULL;

CREATE UNIQUE INDEX uk_data_source_name_active
    ON profiles.data_source (name)
    WHERE archived_at IS NULL;

CREATE INDEX idx_layer_archived_at
    ON iam.layer (archived_at);

CREATE INDEX idx_data_source_archived_at
    ON profiles.data_source (archived_at);

ALTER TABLE iam.layer
    ADD CONSTRAINT ck_layer_archive_actor_consistency
    CHECK ((archived_at IS NULL) = (archived_by IS NULL));

ALTER TABLE profiles.data_source
    ADD CONSTRAINT ck_data_source_archive_actor_consistency
    CHECK ((archived_at IS NULL) = (archived_by IS NULL));

ALTER TABLE iam.layer
    ADD CONSTRAINT fk_layer_archived_by_user
    FOREIGN KEY (archived_by)
    REFERENCES opencop_user (id);

ALTER TABLE profiles.data_source
    ADD CONSTRAINT fk_data_source_archived_by_user
    FOREIGN KEY (archived_by)
    REFERENCES opencop_user (id);
