-- Every connection belongs to an integration, and a connection's access decides which mappers it
-- holds. Both were established for existing rows by Core at startup; from here the schema owns them,
-- so a new database has them too. Storage that only served those startup steps or repeated what is
-- stated elsewhere is dropped.
--
-- Nothing here names a specific integration or mapper, and no row is rewritten: a row breaking
-- either rule fails the migration.

-- 1. Reading needs an ingress mapper and writing an egress mapper; a connection holds nothing else.
-- A database Core has already started on carries this rule, so it is added only where missing.
DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'profiles.data_source'::regclass
           AND conname = 'ck_data_source_access_mappers'
    ) THEN
        ALTER TABLE profiles.data_source
            ADD CONSTRAINT ck_data_source_access_mappers CHECK (
                (access = 'R' AND ingress_mapper_id IS NOT NULL AND egress_mapper_id IS NULL)
                OR (access = 'W' AND ingress_mapper_id IS NULL AND egress_mapper_id IS NOT NULL)
                OR (access = 'RW' AND ingress_mapper_id IS NOT NULL AND egress_mapper_id IS NOT NULL)
            );
    END IF;
END
$$;

-- 2. A connection always names its integration, and with it, through the MATCH FULL key to
-- config.integration_connection_schema, the connection schema version it was set up with.
ALTER TABLE profiles.data_source
    ALTER COLUMN integration_id SET NOT NULL;

-- 3. V59 queued here the sources whose media icons it re-typed, for Core to rebuild their live-map
-- cache. Nothing reads the queue any more, and a database Core has already started on no longer has
-- it.
DROP TABLE IF EXISTS geo.media_icon_cache_repair;

-- 4. Whether an integration contributes a mapper is stated once, on the mapper's identity:
-- profiles.mapper.integration_id is set for a contributed mapper and NULL for one authored as data.
-- The per-revision flag that repeated it is dropped; no ownership is rewritten.
ALTER TABLE profiles.data_mapper
    DROP COLUMN plugin_managed;

-- 5. Data profiles and delimiters are retired: a connection names its own mappers and keeps its
-- framing in its spec, and nothing reads a profile any more. The column goes first, taking its
-- foreign key with it, then each table before the one it references.
ALTER TABLE profiles.data_source
    DROP COLUMN data_profile_id;

DROP TABLE profiles.data_profile;

DROP TABLE profiles.data_delimiter;
