ALTER TABLE config.integration_type
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE profiles.data_source
    ADD COLUMN integration_id UUID,
    ADD COLUMN integration_schema_version INTEGER,
    ADD CONSTRAINT fk_data_source_integration_schema
        FOREIGN KEY (integration_id, integration_schema_version)
        REFERENCES config.integration_type (id, schema_version)
        MATCH FULL;

CREATE INDEX idx_data_source_non_archived_integration
    ON profiles.data_source (integration_id)
    WHERE archived_at IS NULL;
