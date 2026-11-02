-- Mappers gain a direction and an owner, and a connection names one mapper per direction.
--
-- A mapper is INGRESS (decodes what a connection reads) or EGRESS (encodes what a connection
-- writes). profiles.mapper holds one row per mapper identity, so its qualifier and owning
-- integration are stated once; profiles.data_mapper stays the version history under it.
--
-- An owning integration is referenced by its identity only, never by a schema version, so a schema
-- version bump never touches a mapper. config.integration holds that identity and becomes the
-- parent of config.integration_type(id, schema_version).
--
-- Nothing here names a specific integration or mapper. Assigning owners and egress mappers to
-- existing rows, and then enforcing the access rule, is left to Core at startup.

CREATE TYPE profiles.mapper_qualifier AS ENUM ('INGRESS', 'EGRESS');

CREATE TABLE config.integration
(
    id UUID PRIMARY KEY
);

INSERT INTO config.integration (id)
SELECT DISTINCT id
  FROM config.integration_type;

ALTER TABLE config.integration_type
    ADD CONSTRAINT fk_integration_type_integration
        FOREIGN KEY (id) REFERENCES config.integration (id);

CREATE TABLE profiles.mapper
(
    id             UUID                     PRIMARY KEY,
    qualifier      profiles.mapper_qualifier NOT NULL,
    integration_id UUID REFERENCES config.integration (id),
    -- Target of the typed foreign keys below: a column that only takes one direction references
    -- (id, qualifier), so a mapper of the other direction cannot be stored in it.
    CONSTRAINT uk_mapper_id_qualifier UNIQUE (id, qualifier)
);

COMMENT ON TABLE profiles.mapper IS
    'One row per mapper identity; profiles.data_mapper holds its versions.';
COMMENT ON COLUMN profiles.mapper.integration_id IS
    'Integration contributing this mapper from code; NULL for a mapper authored as data.';

-- Every mapper that exists today decodes inbound payloads.
INSERT INTO profiles.mapper (id, qualifier)
SELECT DISTINCT id, 'INGRESS'::profiles.mapper_qualifier
  FROM profiles.data_mapper;

ALTER TABLE profiles.data_mapper
    ADD CONSTRAINT fk_data_mapper_mapper
        FOREIGN KEY (id) REFERENCES profiles.mapper (id);

-- A data profile only ever selects an ingress mapper.
ALTER TABLE profiles.data_profile
    ADD COLUMN mapper_qualifier profiles.mapper_qualifier
        GENERATED ALWAYS AS ('INGRESS'::profiles.mapper_qualifier) STORED,
    ADD CONSTRAINT fk_data_profile_ingress_mapper
        FOREIGN KEY (mapper_id, mapper_qualifier) REFERENCES profiles.mapper (id, qualifier);

-- A connection names its ingress mapper when it reads and its egress mapper when it writes.
ALTER TABLE profiles.data_source
    RENAME COLUMN mapper_id TO ingress_mapper_id;

ALTER TABLE profiles.data_source
    ALTER COLUMN ingress_mapper_id DROP NOT NULL,
    ADD COLUMN egress_mapper_id UUID,
    ADD COLUMN ingress_mapper_qualifier profiles.mapper_qualifier
        GENERATED ALWAYS AS ('INGRESS'::profiles.mapper_qualifier) STORED,
    ADD COLUMN egress_mapper_qualifier profiles.mapper_qualifier
        GENERATED ALWAYS AS ('EGRESS'::profiles.mapper_qualifier) STORED,
    ADD CONSTRAINT fk_data_source_ingress_mapper
        FOREIGN KEY (ingress_mapper_id, ingress_mapper_qualifier)
        REFERENCES profiles.mapper (id, qualifier),
    ADD CONSTRAINT fk_data_source_egress_mapper
        FOREIGN KEY (egress_mapper_id, egress_mapper_qualifier)
        REFERENCES profiles.mapper (id, qualifier);

-- Access decides which mappers a connection holds: reading needs an ingress mapper, writing an
-- egress mapper. That rule is not added here. Existing writable connections have no egress mapper
-- yet, and a check would reject every later update to them, including the Core startup backfills
-- that run before egress mappers are assigned. Core adds ck_data_source_access_mappers once it has
-- assigned egress mappers or downgraded connections to read-only.

CREATE INDEX idx_data_source_egress_mapper
    ON profiles.data_source (egress_mapper_id)
    WHERE egress_mapper_id IS NOT NULL;

COMMENT ON COLUMN profiles.data_source.ingress_mapper_id IS
    'Ingress mapper decoding what this connection reads; runtime uses its latest revision.';
COMMENT ON COLUMN profiles.data_source.egress_mapper_id IS
    'Egress mapper encoding what this connection writes.';
