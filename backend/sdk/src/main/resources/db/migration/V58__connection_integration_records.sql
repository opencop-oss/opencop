-- Integrations' connection extensions store user-created records for each connection, such as the
-- subscriptions a user made. The host stores them for every integration in one table; the
-- extension declares their shape and interprets them.
--
-- Nothing here names a specific integration.

-- 1. A record names its connection together with the integration it was written for, not the
-- schema version: records outlive a schema upgrade within the integration. This key makes the
-- pair referenceable; it is unique because id alone already is, so adding it rewrites no row.
ALTER TABLE profiles.data_source
    ADD CONSTRAINT uq_data_source_integration
        UNIQUE (id, integration_id);

-- 2. Records cannot outlive or change their connection's integration: changing the integration
-- fails while records exist, and deleting the connection deletes them. Archiving keeps them.
CREATE TABLE profiles.connection_integration_records (
    source_id      UUID        NOT NULL,
    integration_id UUID        NOT NULL,
    extension_id   TEXT        NOT NULL,
    collection     TEXT        NOT NULL,
    record_key     TEXT        NOT NULL,
    document       JSONB       NOT NULL,
    revision       BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID        REFERENCES opencop_user (id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     UUID        REFERENCES opencop_user (id),
    CONSTRAINT pk_connection_integration_records
        PRIMARY KEY (source_id, extension_id, collection, record_key),
    CONSTRAINT fk_connection_integration_records_connection
        FOREIGN KEY (source_id, integration_id)
        REFERENCES profiles.data_source (id, integration_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_connection_integration_records_object
        CHECK (jsonb_typeof(document) = 'object'),
    CONSTRAINT ck_connection_integration_records_revision
        CHECK (revision >= 1)
);

-- Lets an integration list its records across all of its connections.
CREATE INDEX idx_connection_integration_records_integration
    ON profiles.connection_integration_records (integration_id, extension_id, collection);

CREATE TRIGGER trg_cir_touch
    BEFORE UPDATE ON profiles.connection_integration_records
    FOR EACH ROW
    EXECUTE FUNCTION profiles.touch_updated_at();

COMMENT ON TABLE profiles.connection_integration_records IS
    'Records an integration''s connection extension stores for one connection, keyed by collection and key.';
COMMENT ON COLUMN profiles.connection_integration_records.revision IS
    'Starts at 1 and grows by one on every write; writers name the revision they expect to replace.';
COMMENT ON COLUMN profiles.connection_integration_records.created_by IS
    'The user whose request created the record; null for a write without a user.';
