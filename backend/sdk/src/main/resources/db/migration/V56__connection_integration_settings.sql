-- A connection may store settings overrides for the integration it selects. The host stores them
-- for every integration in one table; the integration declares and interprets them.
--
-- A missing row means the connection overrides nothing, so no connection needs a row and nothing
-- is backfilled. Defaults are never written.
--
-- Nothing here names a specific integration.

-- 1. The settings row names the connection together with the integration and connection schema
-- version it was validated against. This key makes that triple referenceable; it is unique
-- because id alone already is, so adding it rewrites no connection row.
ALTER TABLE profiles.data_source
    ADD CONSTRAINT uq_data_source_connection_schema
        UNIQUE (id, integration_id, connection_schema_version);

-- 2. Settings that disagree with the connection's integration cannot be stored: changing the
-- connection's integration or schema version fails while settings exist, and deleting the
-- connection deletes them.
CREATE TABLE profiles.connection_integration_settings (
    source_id                 UUID        PRIMARY KEY,
    integration_id            UUID        NOT NULL,
    connection_schema_version INTEGER     NOT NULL,
    settings                  JSONB       NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_connection_integration_settings_connection
        FOREIGN KEY (source_id, integration_id, connection_schema_version)
        REFERENCES profiles.data_source (id, integration_id, connection_schema_version)
        ON DELETE CASCADE,
    -- Readers map the document as sections of fields, so every section must itself be an object;
    -- a scalar, array or null section would fail every read that joins this row. Strict mode keeps
    -- an array section from being unwrapped into its elements.
    CONSTRAINT ck_connection_integration_settings_object
        CHECK (jsonb_typeof(settings) = 'object'
               AND settings <> '{}'::jsonb
               AND NOT jsonb_path_exists(
                   settings, 'strict $.* ? (@.type() != "object")', '{}', true))
);

CREATE TRIGGER trg_cis_touch
    BEFORE UPDATE ON profiles.connection_integration_settings
    FOR EACH ROW
    EXECUTE FUNCTION profiles.touch_updated_at();

COMMENT ON TABLE profiles.connection_integration_settings IS
    'Settings overrides a connection stores for its integration, interpreted under the connection schema version.';
COMMENT ON COLUMN profiles.connection_integration_settings.settings IS
    'Overrides keyed by settings section, then field. Omitted fields inherit the deployment default.';
