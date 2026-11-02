CREATE TABLE config.integration_type
(
    id             UUID        NOT NULL,
    schema_version INTEGER     NOT NULL CHECK (schema_version > 0),
    name           TEXT        NOT NULL CHECK (name ~ '[^[:space:]]'),
    description    TEXT        NOT NULL CHECK (description ~ '[^[:space:]]'),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, schema_version)
);
