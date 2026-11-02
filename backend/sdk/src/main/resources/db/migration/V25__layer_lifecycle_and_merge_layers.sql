CREATE TYPE iam.layer_lifecycle_state AS ENUM (
    'ACTIVE',
    'MERGE_SOURCE_LOCKED',
    'PENDING_MERGE',
    'MERGE_FAILED',
    'ARCHIVED'
);

ALTER TABLE iam.layer
    ADD COLUMN lifecycle_state iam.layer_lifecycle_state NOT NULL DEFAULT 'ACTIVE';

UPDATE iam.layer
SET lifecycle_state = 'ARCHIVED'
WHERE archived_at IS NOT NULL;

ALTER TABLE iam.layer
    ADD CONSTRAINT ck_layer_lifecycle_archive_consistency
    CHECK (
        (archived_at IS NULL AND lifecycle_state <> 'ARCHIVED')
        OR (archived_at IS NOT NULL AND lifecycle_state = 'ARCHIVED')
    );

DROP INDEX IF EXISTS iam.uk_layer_name_active;

CREATE UNIQUE INDEX uk_layer_name_active
    ON iam.layer (name)
    WHERE lifecycle_state IN ('ACTIVE', 'MERGE_SOURCE_LOCKED', 'PENDING_MERGE');

CREATE INDEX idx_layer_lifecycle_state
    ON iam.layer (lifecycle_state);

CREATE OR REPLACE PROCEDURE geo.merge_layers_ordered(
    p_source_layer_ids uuid[],
    p_target_layer_id uuid
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_source_layer_ids IS NULL OR cardinality(p_source_layer_ids) = 0 THEN
        RAISE EXCEPTION 'source layer ids must contain at least one layer';
    END IF;

    IF p_target_layer_id = ANY (p_source_layer_ids) THEN
        RAISE EXCEPTION 'target layer % cannot also be a source layer', p_target_layer_id;
    END IF;

    PERFORM 1
    FROM iam.layer l
    WHERE l.id = p_target_layer_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'target layer % does not exist', p_target_layer_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM geo.features f
        WHERE f.source = p_target_layer_id
    ) THEN
        RAISE EXCEPTION 'target layer % already has features', p_target_layer_id;
    END IF;

    CREATE TEMP TABLE IF NOT EXISTS pg_temp.merge_retained_features (
        source_layer_id uuid NOT NULL,
        object_id varchar(255) NOT NULL,
        source_event_id uuid NOT NULL,
        target_event_id uuid NOT NULL,
        source_order integer NOT NULL,
        playback_at timestamptz NOT NULL,
        created_at timestamptz NOT NULL,
        stale_at timestamptz,
        first_emission_at timestamptz NOT NULL,
        PRIMARY KEY (source_layer_id, object_id)
    ) ON COMMIT DROP;

    TRUNCATE pg_temp.merge_retained_features;

    INSERT INTO pg_temp.merge_retained_features (
        source_layer_id,
        object_id,
        source_event_id,
        target_event_id,
        source_order,
        playback_at,
        created_at,
        stale_at,
        first_emission_at
    )
    WITH ordered_sources AS (
        SELECT source_layer_id, source_order
        FROM unnest(p_source_layer_ids) WITH ORDINALITY AS source_ordering(source_layer_id, source_order)
    ),
    ordered_helpers AS (
        SELECT
            helper.source,
            helper.object_id,
            helper.event_id,
            source_ordering.source_order,
            helper.playback_at,
            helper.created_at,
            helper.stale_at,
            helper.first_emission_at,
            row_number() OVER (
                PARTITION BY helper.object_id
                ORDER BY source_ordering.source_order
            ) AS collision_rank
        FROM ordered_sources source_ordering
        JOIN geo.feature_latest_active_helper helper
          ON helper.source = source_ordering.source_layer_id
        WHERE helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP
    )
    SELECT
        source,
        object_id,
        event_id,
        gen_random_uuid(),
        source_order,
        playback_at,
        created_at,
        stale_at,
        first_emission_at
    FROM ordered_helpers
    WHERE collision_rank = 1;

    CREATE TEMP TABLE IF NOT EXISTS pg_temp.merge_media_map (
        original_media_id uuid PRIMARY KEY,
        cloned_media_id uuid NOT NULL
    ) ON COMMIT DROP;

    TRUNCATE pg_temp.merge_media_map;

    INSERT INTO pg_temp.merge_media_map (original_media_id, cloned_media_id)
    WITH RECURSIVE directly_referenced_media AS (
        SELECT DISTINCT lm.media_id
        FROM layer_media lm
        JOIN unnest(p_source_layer_ids) AS source_layer(source_id)
          ON source_layer.source_id = lm.layer_id

        UNION

        SELECT DISTINCT ea.attachment_id AS media_id
        FROM event_attachment ea
        JOIN pg_temp.merge_retained_features retained
          ON retained.source_layer_id = ea.source_id
         AND retained.object_id = ea.object_id
    ),
    media_tree AS (
        SELECT drm.media_id AS id
        FROM directly_referenced_media drm

        UNION

        SELECT child.id
        FROM media_file child
        JOIN media_tree mt
          ON child.thumbnail_of = mt.id
    )
    SELECT DISTINCT mt.id, gen_random_uuid()
    FROM media_tree mt;

    INSERT INTO media_attachment (id, creator_id, owner_id, access, created_at, usage)
    SELECT
        media_map.cloned_media_id,
        media_attachment.creator_id,
        media_attachment.owner_id,
        media_attachment.access,
        media_attachment.created_at,
        media_attachment.usage
    FROM media_attachment
    JOIN pg_temp.merge_media_map media_map
      ON media_map.original_media_id = media_attachment.id;

    INSERT INTO media_file (
        id,
        blob_key,
        original_filename,
        content_type,
        size_bytes,
        mime_type,
        attributes,
        thumbnail_of
    )
    SELECT
        media_map.cloned_media_id,
        media_file.blob_key,
        media_file.original_filename,
        media_file.content_type,
        media_file.size_bytes,
        media_file.mime_type,
        media_file.attributes,
        parent_map.cloned_media_id
    FROM media_file
    JOIN pg_temp.merge_media_map media_map
      ON media_map.original_media_id = media_file.id
    LEFT JOIN pg_temp.merge_media_map parent_map
      ON parent_map.original_media_id = media_file.thumbnail_of;

    INSERT INTO media_url (id, url)
    SELECT
        media_map.cloned_media_id,
        media_url.url
    FROM media_url
    JOIN pg_temp.merge_media_map media_map
      ON media_map.original_media_id = media_url.id;

    INSERT INTO geo.features (
        id,
        feature_kind,
        source,
        object_id,
        created_at,
        start_at,
        stale_at,
        geom,
        payload
    )
    WITH patched_features AS (
        SELECT
            source_features.*,
            retained.target_event_id,
            jsonb_set(
                jsonb_set(
                    COALESCE(
                        source_features.payload -> 'opencop',
                        source_features.payload #> '{properties,opencop}',
                        '{}'::jsonb
                    ),
                    '{id}',
                    to_jsonb(retained.target_event_id::text),
                    true
                ),
                '{source}',
                to_jsonb(p_target_layer_id::text),
                true
            ) AS patched_opencop,
            jsonb_set(
                jsonb_set(
                    source_features.payload,
                    '{id}',
                    to_jsonb(retained.target_event_id::text),
                    true
                ),
                '{source}',
                to_jsonb(p_target_layer_id::text),
                true
            ) AS payload_with_rewritten_identifiers
        FROM pg_temp.merge_retained_features retained
        JOIN geo.features source_features
          ON source_features.id = retained.source_event_id
    )
    SELECT
        target_event_id,
        feature_kind,
        p_target_layer_id,
        object_id,
        created_at,
        start_at,
        stale_at,
        geom,
        CASE
            WHEN payload #> '{properties,opencop}' IS NOT NULL THEN
                jsonb_set(
                    CASE
                        WHEN payload -> 'opencop' IS NOT NULL THEN
                            jsonb_set(
                                payload_with_rewritten_identifiers,
                                '{opencop}',
                                patched_opencop,
                                false
                            )
                        ELSE
                            payload_with_rewritten_identifiers
                    END,
                    '{properties,opencop}',
                    patched_opencop,
                    false
                )
            WHEN payload -> 'opencop' IS NOT NULL THEN
                jsonb_set(
                    payload_with_rewritten_identifiers,
                    '{opencop}',
                    patched_opencop,
                    false
                )
            ELSE
                payload_with_rewritten_identifiers
        END
    FROM patched_features;

    INSERT INTO geo.feature_latest_active_helper (
        source,
        object_id,
        event_id,
        playback_at,
        created_at,
        stale_at,
        first_emission_at
    )
    SELECT
        p_target_layer_id,
        object_id,
        target_event_id,
        playback_at,
        created_at,
        stale_at,
        first_emission_at
    FROM pg_temp.merge_retained_features;

    INSERT INTO geo.remarks (
        id,
        source_id,
        object_id,
        content,
        user_id,
        created_at,
        updated_at
    )
    SELECT
        gen_random_uuid(),
        p_target_layer_id,
        remarks.object_id,
        remarks.content,
        remarks.user_id,
        remarks.created_at,
        remarks.updated_at
    FROM geo.remarks remarks
    JOIN pg_temp.merge_retained_features retained
      ON retained.source_layer_id = remarks.source_id
     AND retained.object_id = remarks.object_id;

    INSERT INTO event_attachment (source_id, object_id, attachment_id)
    SELECT
        p_target_layer_id,
        event_attachment.object_id,
        media_map.cloned_media_id
    FROM event_attachment
    JOIN pg_temp.merge_retained_features retained
      ON retained.source_layer_id = event_attachment.source_id
     AND retained.object_id = event_attachment.object_id
    JOIN pg_temp.merge_media_map media_map
      ON media_map.original_media_id = event_attachment.attachment_id
    ON CONFLICT DO NOTHING;

    INSERT INTO layer_media (layer_id, media_id)
    SELECT
        p_target_layer_id,
        media_map.cloned_media_id
    FROM layer_media
    JOIN unnest(p_source_layer_ids) AS source_layer(source_id)
      ON source_layer.source_id = layer_media.layer_id
    JOIN pg_temp.merge_media_map media_map
      ON media_map.original_media_id = layer_media.media_id
    ON CONFLICT DO NOTHING;

    INSERT INTO iam.server_layer_access (server_id, layer_id, role)
    WITH role_rank AS (
        SELECT
            access.server_id,
            access.role,
            CASE access.role
                WHEN 'OWNER'::iam.server_role THEN 3
                WHEN 'ADMIN'::iam.server_role THEN 2
                ELSE 1
            END AS rank
        FROM iam.server_layer_access access
        JOIN unnest(p_source_layer_ids) AS source_layer(source_id)
          ON source_layer.source_id = access.layer_id
    ),
    highest_role AS (
        SELECT DISTINCT ON (server_id)
            server_id,
            role
        FROM role_rank
        ORDER BY server_id, rank DESC
    )
    SELECT server_id, p_target_layer_id, role
    FROM highest_role
    ON CONFLICT (server_id, layer_id) DO UPDATE
    SET role =
        CASE
            WHEN EXCLUDED.role = 'OWNER'::iam.server_role THEN EXCLUDED.role
            WHEN iam.server_layer_access.role = 'OWNER'::iam.server_role THEN iam.server_layer_access.role
            WHEN EXCLUDED.role = 'ADMIN'::iam.server_role THEN EXCLUDED.role
            ELSE iam.server_layer_access.role
        END;
END;
$$;
