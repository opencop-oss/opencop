CREATE TYPE iam.layer_access_scope AS ENUM ('PRIVATE', 'GLOBAL');

ALTER TABLE iam.layer
    ADD COLUMN access_scope iam.layer_access_scope NOT NULL DEFAULT 'PRIVATE';

CREATE INDEX idx_layer_global_access_scope
    ON iam.layer (access_scope)
    WHERE access_scope = 'GLOBAL';
