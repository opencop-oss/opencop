CREATE OR REPLACE PROCEDURE geo.clone_source_to_layer(
    p_source_id uuid,
    p_target_layer_id uuid,
    p_copy_remarks boolean DEFAULT true,
    p_copy_media boolean DEFAULT true
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_source_id = p_target_layer_id THEN
        RAISE EXCEPTION 'source id and target layer id must differ: %', p_source_id;
    END IF;

    PERFORM 1
    FROM iam.layer l
    WHERE l.id = p_target_layer_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'target layer % does not exist', p_target_layer_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM geo.features f
        WHERE f.source = p_source_id
    ) THEN
        RAISE EXCEPTION 'source % has no features to clone', p_source_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM geo.features f
        WHERE f.source = p_target_layer_id
    ) THEN
        RAISE EXCEPTION 'target layer % already has features', p_target_layer_id;
    END IF;

    IF p_copy_media THEN
        CREATE TEMP TABLE IF NOT EXISTS pg_temp.clone_media_map (
            original_media_id uuid PRIMARY KEY,
            cloned_media_id uuid NOT NULL
        ) ON COMMIT DROP;

        TRUNCATE pg_temp.clone_media_map;

        INSERT INTO pg_temp.clone_media_map (
            original_media_id,
            cloned_media_id
        )
        WITH RECURSIVE directly_referenced_media AS (
            SELECT DISTINCT lm.media_id
            FROM layer_media lm
            WHERE lm.layer_id = p_source_id

            UNION

            SELECT DISTINCT em.media_id
            FROM event_media em
            WHERE em.source_id = p_source_id
        ),
        media_tree AS (
            SELECT m.id
            FROM media m
            JOIN directly_referenced_media drm
              ON drm.media_id = m.id

            UNION

            SELECT child.id
            FROM media child
            JOIN media_tree mt
              ON child.thumbnail_of = mt.id
        )
        SELECT DISTINCT
            mt.id,
            gen_random_uuid()
        FROM media_tree mt;

        INSERT INTO media (
            id,
            blob_key,
            original_filename,
            content_type,
            size_bytes,
            access,
            creator_id,
            owner_id,
            created_at,
            mime_type,
            attributes,
            thumbnail_of,
            usage
        )
        SELECT
            cmm.cloned_media_id,
            m.blob_key,
            m.original_filename,
            m.content_type,
            m.size_bytes,
            m.access,
            m.creator_id,
            m.owner_id,
            m.created_at,
            m.mime_type,
            m.attributes,
            parent_map.cloned_media_id,
            m.usage
        FROM media m
        JOIN pg_temp.clone_media_map cmm
          ON cmm.original_media_id = m.id
        LEFT JOIN pg_temp.clone_media_map parent_map
          ON parent_map.original_media_id = m.thumbnail_of;
    END IF;

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
    WITH source_features AS (
        SELECT
            f.*,
            gen_random_uuid() AS new_feature_id
        FROM geo.features f
        WHERE f.source = p_source_id
    ),
    patched_features AS (
        SELECT
            sf.*,
            jsonb_set(
                jsonb_set(
                    COALESCE(
                        sf.payload -> 'opencop',
                        sf.payload #> '{properties,opencop}',
                        '{}'::jsonb
                    ),
                    '{id}',
                    to_jsonb(sf.new_feature_id::text),
                    true
                ),
                '{source}',
                to_jsonb(p_target_layer_id::text),
                true
            ) AS patched_opencop,
            jsonb_set(
                jsonb_set(
                    sf.payload,
                    '{id}',
                    to_jsonb(sf.new_feature_id::text),
                    true
                ),
                '{source}',
                to_jsonb(p_target_layer_id::text),
                true
            ) AS payload_with_rewritten_identifiers
        FROM source_features sf
    )
    SELECT
        pf.new_feature_id,
        pf.feature_kind,
        p_target_layer_id,
        pf.object_id,
        pf.created_at,
        pf.start_at,
        pf.stale_at,
        pf.geom,
        CASE
            WHEN pf.payload #> '{properties,opencop}' IS NOT NULL THEN
                jsonb_set(
                    CASE
                        WHEN pf.payload -> 'opencop' IS NOT NULL THEN
                            jsonb_set(
                                pf.payload_with_rewritten_identifiers,
                                '{opencop}',
                                pf.patched_opencop,
                                false
                            )
                        ELSE
                            pf.payload_with_rewritten_identifiers
                    END,
                    '{properties,opencop}',
                    pf.patched_opencop,
                    false
                )
            WHEN pf.payload -> 'opencop' IS NOT NULL THEN
                jsonb_set(
                    pf.payload_with_rewritten_identifiers,
                    '{opencop}',
                    pf.patched_opencop,
                    false
                )
            ELSE
                pf.payload_with_rewritten_identifiers
        END
    FROM patched_features pf;

    IF p_copy_remarks THEN
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
            r.object_id,
            r.content,
            r.user_id,
            r.created_at,
            r.updated_at
        FROM geo.remarks r
        WHERE r.source_id = p_source_id;
    END IF;

    IF p_copy_media THEN
        INSERT INTO event_media (
            source_id,
            object_id,
            media_id
        )
        SELECT
            p_target_layer_id,
            em.object_id,
            cmm.cloned_media_id
        FROM event_media em
        JOIN pg_temp.clone_media_map cmm
          ON cmm.original_media_id = em.media_id
        WHERE em.source_id = p_source_id
        ON CONFLICT DO NOTHING;

        INSERT INTO layer_media (
            layer_id,
            media_id
        )
        SELECT
            p_target_layer_id,
            cmm.cloned_media_id
        FROM layer_media lm
        JOIN pg_temp.clone_media_map cmm
          ON cmm.original_media_id = lm.media_id
        WHERE lm.layer_id = p_source_id
        ON CONFLICT DO NOTHING;
    END IF;
END;
$$;
