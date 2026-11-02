-- A socket connection's security moves under its transport, and the spec keeps only what Connect
-- runs: a TCP spec carries transport.security (none or mtls), UDP and RTSP carry none at all. The
-- root security, auth, and heartbeat keys go. Connect never read auth or heartbeat, and a socket
-- connection's only credentials are its mTLS client certificate, already held by its security.
--
-- Kafka connections store their own spec shape and are left as they are.

-- The rewrite is not a user edit of the connection, so the updated_at trigger is off while it runs.
ALTER TABLE profiles.data_source DISABLE TRIGGER trg_ds_touch;

DO $$
DECLARE
    unsupported_count INTEGER;
BEGIN
    -- Archived connections are read back too, so every row must fit the new spec.
    SELECT count(*) INTO unsupported_count
      FROM profiles.data_source
     WHERE provider IN ('TCP', 'UDP', 'RTSP')
       AND (COALESCE(connection_spec #>> '{auth,type}', 'none') <> 'none'
            OR (connection_spec -> 'security' IS NOT NULL
                AND NOT ((provider = 'TCP'
                          AND COALESCE(connection_spec #>> '{security,type}', '') IN ('none', 'mtls'))
                      OR (provider IN ('UDP', 'RTSP')
                          AND COALESCE(connection_spec #>> '{security,type}', '') = 'none')))
            -- A TCP spec must end up with a security under its transport.
            OR (provider = 'TCP'
                AND connection_spec -> 'security' IS NULL
                AND connection_spec #> '{transport,security}' IS NULL));

    IF unsupported_count > 0 THEN
        RAISE EXCEPTION
            'connection_spec transport security: % socket connection(s) store a security or auth no connector runs',
            unsupported_count;
    END IF;
END $$;

UPDATE profiles.data_source
   SET connection_spec = (connection_spec - 'security' - 'auth' - 'heartbeat')
       || jsonb_build_object(
              'transport',
              CASE
                  WHEN provider = 'TCP' AND connection_spec -> 'security' IS NOT NULL
                      THEN (connection_spec -> 'transport')
                           || jsonb_build_object('security', connection_spec -> 'security')
                  ELSE connection_spec -> 'transport'
              END)
 WHERE provider IN ('TCP', 'UDP', 'RTSP')
   AND (connection_spec -> 'security' IS NOT NULL
        OR connection_spec -> 'auth' IS NOT NULL
        OR connection_spec -> 'heartbeat' IS NOT NULL);

ALTER TABLE profiles.data_source ENABLE TRIGGER trg_ds_touch;
