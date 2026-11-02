-- Introduce media_attachment as a parent table holding fields common to all attachment
-- types. media_file (file blobs) and media_url (external URLs) are child tables whose
-- primary key is a FK to media_attachment. A single event_attachment join table replaces
-- the former event_media and event_media_url pair.

-- Parent table: shared identity and access fields
CREATE TABLE media_attachment (
    id         UUID PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES opencop_user(id),
    owner_id   UUID NOT NULL REFERENCES opencop_user(id),
    access     media_access NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    usage      media_usage
);

CREATE INDEX idx_media_attachment_creator_id ON media_attachment (creator_id);
CREATE INDEX idx_media_attachment_created_at ON media_attachment (created_at DESC);

-- Populate from existing media rows
INSERT INTO media_attachment (id, creator_id, owner_id, access, created_at, usage)
SELECT id, creator_id, owner_id, access, created_at, usage FROM media;

-- File-specific child table
CREATE TABLE media_file (
    id                UUID PRIMARY KEY REFERENCES media_attachment(id) ON DELETE CASCADE,
    blob_key          TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    content_type      media_content_type NOT NULL,
    size_bytes        BIGINT NOT NULL,
    mime_type         TEXT,
    attributes        JSONB,
    thumbnail_of      UUID REFERENCES media_file(id)
);

CREATE INDEX idx_media_file_thumbnail_of ON media_file (thumbnail_of) WHERE thumbnail_of IS NOT NULL;

-- Populate from existing media rows
INSERT INTO media_file (id, blob_key, original_filename, content_type, size_bytes, mime_type, attributes, thumbnail_of)
SELECT id, blob_key, original_filename, content_type, size_bytes, mime_type, attributes, thumbnail_of FROM media;

-- URL-specific child table
CREATE TABLE media_url (
    id  UUID PRIMARY KEY REFERENCES media_attachment(id) ON DELETE CASCADE,
    url TEXT NOT NULL
);

-- Unified event join table: replaces event_media and (the forthcoming) event_media_url
CREATE TABLE event_attachment (
    source_id     UUID NOT NULL,
    object_id     TEXT NOT NULL,
    attachment_id UUID NOT NULL REFERENCES media_attachment(id) ON DELETE CASCADE,
    PRIMARY KEY (source_id, object_id, attachment_id)
);

CREATE INDEX idx_event_attachment_source_object ON event_attachment (source_id, object_id);

-- Migrate existing event_media rows
INSERT INTO event_attachment (source_id, object_id, attachment_id)
SELECT source_id, object_id, media_id FROM event_media;

-- Update layer_media FK to reference media_file
ALTER TABLE layer_media DROP CONSTRAINT layer_media_media_id_fkey;
ALTER TABLE layer_media ADD CONSTRAINT layer_media_media_id_fkey
    FOREIGN KEY (media_id) REFERENCES media_file(id) ON DELETE CASCADE;

-- Update avatar FK to reference media_file
ALTER TABLE opencop_user_preference DROP CONSTRAINT IF EXISTS opencop_user_preference_avatar_media_id_fkey;
ALTER TABLE opencop_user_preference ADD CONSTRAINT opencop_user_preference_avatar_media_id_fkey
    FOREIGN KEY (avatar_media_id) REFERENCES media_file(id) ON DELETE SET NULL;

-- Drop superseded tables (event_media first because media has a FK from event_media)
DROP TABLE event_media;
DROP TABLE media;

-- Update geo.clone_source_to_layer to use the new table layout above.
-- V7 was written against media/event_media, both of which are now dropped.
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

        -- Collect all file-based media reachable from this source (event attachments +
        -- layer icons) plus any thumbnail children, and assign new IDs for the clones.
        -- URL-type attachments exist only in media_url (not media_file) so they are
        -- naturally excluded by the JOIN to media_file inside media_tree.
        INSERT INTO pg_temp.clone_media_map (original_media_id, cloned_media_id)
        WITH RECURSIVE directly_referenced_media AS (
            SELECT DISTINCT lm.media_id
            FROM layer_media lm
            WHERE lm.layer_id = p_source_id

            UNION

            SELECT DISTINCT ea.attachment_id AS media_id
            FROM event_attachment ea
            WHERE ea.source_id = p_source_id
        ),
        media_tree AS (
            SELECT mf.id
            FROM media_file mf
            JOIN directly_referenced_media drm ON drm.media_id = mf.id

            UNION

            SELECT child.id
            FROM media_file child
            JOIN media_tree mt ON child.thumbnail_of = mt.id
        )
        SELECT DISTINCT mt.id, gen_random_uuid()
        FROM media_tree mt;

        -- Parent rows first (media_file.id FK → media_attachment.id)
        INSERT INTO media_attachment (id, creator_id, owner_id, access, created_at, usage)
        SELECT
            cmm.cloned_media_id,
            ma.creator_id,
            ma.owner_id,
            ma.access,
            ma.created_at,
            ma.usage
        FROM media_attachment ma
        JOIN pg_temp.clone_media_map cmm ON cmm.original_media_id = ma.id;

        -- File child rows; rewrite thumbnail_of to point at the cloned parent
        INSERT INTO media_file (
            id, blob_key, original_filename, content_type, size_bytes, mime_type,
            attributes, thumbnail_of
        )
        SELECT
            cmm.cloned_media_id,
            mf.blob_key,
            mf.original_filename,
            mf.content_type,
            mf.size_bytes,
            mf.mime_type,
            mf.attributes,
            parent_map.cloned_media_id
        FROM media_file mf
        JOIN pg_temp.clone_media_map cmm ON cmm.original_media_id = mf.id
        LEFT JOIN pg_temp.clone_media_map parent_map ON parent_map.original_media_id = mf.thumbnail_of;
    END IF;

    INSERT INTO geo.features (
        id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload
    )
    WITH source_features AS (
        SELECT f.*, gen_random_uuid() AS new_feature_id
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
                    '{id}',   to_jsonb(sf.new_feature_id::text), true
                ),
                '{source}', to_jsonb(p_target_layer_id::text),  true
            ) AS patched_opencop,
            jsonb_set(
                jsonb_set(sf.payload, '{id}',     to_jsonb(sf.new_feature_id::text), true),
                            '{source}', to_jsonb(p_target_layer_id::text), true
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
                            jsonb_set(pf.payload_with_rewritten_identifiers, '{opencop}', pf.patched_opencop, false)
                        ELSE pf.payload_with_rewritten_identifiers
                    END,
                    '{properties,opencop}', pf.patched_opencop, false
                )
            WHEN pf.payload -> 'opencop' IS NOT NULL THEN
                jsonb_set(pf.payload_with_rewritten_identifiers, '{opencop}', pf.patched_opencop, false)
            ELSE
                pf.payload_with_rewritten_identifiers
        END
    FROM patched_features pf;

    IF p_copy_remarks THEN
        INSERT INTO geo.remarks (id, source_id, object_id, content, user_id, created_at, updated_at)
        SELECT gen_random_uuid(), p_target_layer_id, r.object_id, r.content, r.user_id, r.created_at, r.updated_at
        FROM geo.remarks r
        WHERE r.source_id = p_source_id;
    END IF;

    IF p_copy_media THEN
        INSERT INTO event_attachment (source_id, object_id, attachment_id)
        SELECT p_target_layer_id, ea.object_id, cmm.cloned_media_id
        FROM event_attachment ea
        JOIN pg_temp.clone_media_map cmm ON cmm.original_media_id = ea.attachment_id
        WHERE ea.source_id = p_source_id
        ON CONFLICT DO NOTHING;

        INSERT INTO layer_media (layer_id, media_id)
        SELECT p_target_layer_id, cmm.cloned_media_id
        FROM layer_media lm
        JOIN pg_temp.clone_media_map cmm ON cmm.original_media_id = lm.media_id
        WHERE lm.layer_id = p_source_id
        ON CONFLICT DO NOTHING;
    END IF;
END;
$$;
