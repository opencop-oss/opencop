-- An integration and the versions of the connection contract it offers are separate things.
--
-- config.integration is the integration itself: its identity, display metadata and activation.
-- config.integration_type held one row per schema version, repeating the display metadata and
-- activation on every version. It becomes config.integration_connection_schema and keeps only
-- what versions the connection contract; a connection still binds to one exact version.
--
-- Nothing here names a specific integration.

-- Every integration identity must have a schema version to take its display metadata from.
DO $$
DECLARE
    unversioned TEXT;
BEGIN
    SELECT string_agg(i.id::text, ', ') INTO unversioned
      FROM config.integration i
     WHERE NOT EXISTS (SELECT 1 FROM config.integration_type t WHERE t.id = i.id);
    IF unversioned IS NOT NULL THEN
        RAISE EXCEPTION 'Integrations with no schema version to take display metadata from: %',
            unversioned;
    END IF;
END
$$;

-- Activation is identity-wide in behaviour, so every schema version of an integration must
-- already agree. A disagreement needs a person to decide, so it stops the migration.
DO $$
DECLARE
    disagreeing TEXT;
BEGIN
    SELECT string_agg(id::text, ', ') INTO disagreeing
      FROM (SELECT id
              FROM config.integration_type
             GROUP BY id
            HAVING bool_and(active) <> bool_or(active)) d;
    IF disagreeing IS NOT NULL THEN
        RAISE EXCEPTION 'Integrations with disagreeing activation across schema versions: %',
            disagreeing;
    END IF;
END
$$;

-- 1. Identity data moves to the identity. Display metadata comes from each integration's highest
-- schema version, the one its plugin publishes today.
ALTER TABLE config.integration
    ADD COLUMN name        TEXT,
    ADD COLUMN description TEXT,
    ADD COLUMN active      BOOLEAN,
    ADD COLUMN created_at  TIMESTAMPTZ,
    ADD COLUMN updated_at  TIMESTAMPTZ;

UPDATE config.integration i
   SET name        = latest.name,
       description = latest.description,
       active      = versions.active,
       created_at  = versions.created_at,
       updated_at  = versions.updated_at
  FROM (SELECT DISTINCT ON (id) id, name, description
          FROM config.integration_type
         ORDER BY id, schema_version DESC) latest,
       (SELECT id,
               bool_and(active) AS active,
               min(created_at)  AS created_at,
               max(updated_at)  AS updated_at
          FROM config.integration_type
         GROUP BY id) versions
 WHERE latest.id = i.id
   AND versions.id = i.id;

ALTER TABLE config.integration
    ALTER COLUMN name        SET NOT NULL,
    ALTER COLUMN description SET NOT NULL,
    ALTER COLUMN active      SET NOT NULL,
    ALTER COLUMN active      SET DEFAULT TRUE,
    ALTER COLUMN created_at  SET NOT NULL,
    ALTER COLUMN created_at  SET DEFAULT now(),
    ALTER COLUMN updated_at  SET NOT NULL,
    ALTER COLUMN updated_at  SET DEFAULT now(),
    ADD CONSTRAINT ck_integration_name_not_blank CHECK (name ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_integration_description_not_blank CHECK (description ~ '[^[:space:]]');

COMMENT ON TABLE config.integration IS
    'An integration a plugin provides: its identity, display metadata and activation.';
COMMENT ON COLUMN config.integration.active IS
    'Whether new connections may select this integration.';

-- 2. The version table keeps only the version.
ALTER TABLE config.integration_type RENAME TO integration_connection_schema;

ALTER TABLE config.integration_connection_schema
    RENAME COLUMN id TO integration_id;

ALTER TABLE config.integration_connection_schema
    DROP COLUMN name,
    DROP COLUMN description,
    DROP COLUMN active,
    DROP COLUMN updated_at;

ALTER TABLE config.integration_connection_schema
    RENAME CONSTRAINT integration_type_pkey TO integration_connection_schema_pkey;

ALTER TABLE config.integration_connection_schema
    RENAME CONSTRAINT fk_integration_type_integration
                   TO fk_integration_connection_schema_integration;

ALTER TABLE config.integration_connection_schema
    RENAME CONSTRAINT integration_type_schema_version_check
                   TO integration_connection_schema_schema_version_check;

COMMENT ON TABLE config.integration_connection_schema IS
    'One version of the connection contract an integration offers.';

-- 3. A connection binds to one connection schema version; integration_id stays the identity. The
-- foreign key follows the renamed table and columns and stays MATCH FULL. Renames fire no
-- trigger, so no connection's updated_at changes.
ALTER TABLE profiles.data_source
    RENAME COLUMN integration_schema_version TO connection_schema_version;

ALTER TABLE profiles.data_source
    RENAME CONSTRAINT fk_data_source_integration_schema TO fk_data_source_connection_schema;
