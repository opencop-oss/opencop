ALTER TABLE iam.layer
    ADD COLUMN default_visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE profiles.data_source
    ADD COLUMN default_visible BOOLEAN NOT NULL DEFAULT TRUE;