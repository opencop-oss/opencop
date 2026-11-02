-- Every connection names its own mapper; runtime uses that mapper's latest revision.
--
-- data_profile_id stays but becomes optional: v1 callers still select a data profile, which is
-- translated into the connection's mapper once, at creation, and a connection created without one
-- stores NULL. The foreign key stays, so a stored profile must still exist.

ALTER TABLE profiles.data_source
    ADD COLUMN mapper_id UUID;

-- The backfill is not a user edit of the connection, so the updated_at trigger is off while it runs.
ALTER TABLE profiles.data_source DISABLE TRIGGER trg_ds_touch;

DO $$
DECLARE
    candidate_count  INTEGER;
    updated_count    INTEGER;
    unresolved_count INTEGER;
BEGIN
    SELECT count(*) INTO candidate_count FROM profiles.data_source;

    UPDATE profiles.data_source ds
       SET mapper_id = p.mapper_id
      FROM profiles.data_profile p
     WHERE p.id = ds.data_profile_id;
    GET DIAGNOSTICS updated_count = ROW_COUNT;

    SELECT count(*) INTO unresolved_count
      FROM profiles.data_source ds
     WHERE NOT EXISTS (SELECT 1 FROM profiles.data_mapper m WHERE m.id = ds.mapper_id);

    RAISE NOTICE 'Connection mapper backfill: candidates=%, updated=%, unresolved=%',
        candidate_count, updated_count, unresolved_count;

    IF unresolved_count > 0 OR updated_count <> candidate_count THEN
        RAISE EXCEPTION 'Connection mapper backfill left % of % connections unresolved',
            unresolved_count, candidate_count;
    END IF;
END
$$;

ALTER TABLE profiles.data_source ENABLE TRIGGER trg_ds_touch;

ALTER TABLE profiles.data_source
    ALTER COLUMN mapper_id SET NOT NULL;

ALTER TABLE profiles.data_source
    ALTER COLUMN data_profile_id DROP NOT NULL;

COMMENT ON COLUMN profiles.data_source.mapper_id IS
    'Mapper selected by this connection; runtime uses its latest revision.';
COMMENT ON COLUMN profiles.data_source.data_profile_id IS
    'Deprecated: legacy v1 data-profile selection, translated into mapper_id. Not read at runtime.';

-- A socket data source's listen port lives in its connection spec, so the rule that at most one
-- active UDP source holds a port now reads it from there instead of from the port column.
-- The expression is UdpConfig.listenPort(): localPort when set, otherwise port. Change both
-- together. Core has only ever stored a null localPort, so this computes the value the port
-- column already held and cannot reject a deployed row the previous index accepted.
-- The name is kept: Core maps a violation of this constraint to a listen-port conflict.
DROP INDEX profiles.uk_data_source_listen_port_active;

CREATE UNIQUE INDEX uk_data_source_listen_port_active
    ON profiles.data_source (
        provider,
        (COALESCE(connection_spec #>> '{transport,localPort}',
                  connection_spec #>> '{transport,port}')::integer))
    WHERE provider = 'UDP'::profiles.transport_kind
      AND archived_at IS NULL;

-- host and port restated the endpoint and listen port a socket data source's connection spec
-- already holds, and nothing reads them now that the listen-port index keys on the spec.
ALTER TABLE profiles.data_source
    DROP COLUMN host,
    DROP COLUMN port;
