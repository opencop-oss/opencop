CREATE INDEX geofeatures_source_object_time
    ON geo.features (source, object_id, created_at DESC);

DROP INDEX IF EXISTS geofeatures_event_time;

ALTER TABLE geo.features
    ADD CONSTRAINT chk_geo_features_payload_source_matches
        CHECK (payload #>> '{opencop,source}' = source::text) NOT VALID;

ALTER TABLE geo.features
    ADD CONSTRAINT chk_geo_features_payload_object_id_matches
        CHECK (payload #>> '{opencop,objectId}' = object_id) NOT VALID;

ALTER TABLE geo.remarks
    ADD COLUMN source_id UUID;

DELETE FROM geo.remarks r
WHERE NOT EXISTS (
    SELECT 1
    FROM geo.features f
    WHERE f.object_id = r.object_id
);

WITH object_sources AS (
    SELECT object_id,
           source,
           ROW_NUMBER() OVER (PARTITION BY object_id ORDER BY source) AS source_rank
    FROM (
        SELECT DISTINCT object_id, source
        FROM geo.features
    ) distinct_sources
),
updated_remarks AS (
    UPDATE geo.remarks r
    SET source_id = os.source
    FROM object_sources os
    WHERE r.object_id = os.object_id
      AND os.source_rank = 1
      AND r.source_id IS NULL
    RETURNING r.id, r.object_id, r.content, r.user_id, r.created_at, r.updated_at
)
INSERT INTO geo.remarks (id, source_id, object_id, content, user_id, created_at, updated_at)
SELECT gen_random_uuid(),
       os.source,
       ur.object_id,
       ur.content,
       ur.user_id,
       ur.created_at,
       ur.updated_at
FROM updated_remarks ur
JOIN object_sources os
  ON os.object_id = ur.object_id
 AND os.source_rank > 1;

ALTER TABLE geo.remarks
    ALTER COLUMN source_id SET NOT NULL;

CREATE INDEX idx_remarks_source_object_created
    ON geo.remarks(source_id, object_id, created_at DESC);

DROP INDEX IF EXISTS idx_remarks_event_created;


ALTER TABLE event_media
    RENAME COLUMN event_id TO object_id;

DELETE FROM event_media em
WHERE NOT EXISTS (
    SELECT 1
    FROM geo.features f
    WHERE f.object_id = em.object_id
);

ALTER TABLE event_media
    ADD COLUMN source_id UUID;

ALTER TABLE event_media
    DROP CONSTRAINT event_media_pkey;

WITH object_sources AS (
    SELECT object_id,
           source,
           ROW_NUMBER() OVER (PARTITION BY object_id ORDER BY source) AS source_rank
    FROM (
        SELECT DISTINCT object_id, source
        FROM geo.features
    ) distinct_sources
),
updated_media AS (
    UPDATE event_media em
    SET source_id = os.source
    FROM object_sources os
    WHERE em.object_id = os.object_id
      AND os.source_rank = 1
      AND em.source_id IS NULL
    RETURNING em.object_id, em.media_id
)
INSERT INTO event_media (source_id, object_id, media_id)
SELECT os.source,
       um.object_id,
       um.media_id
FROM updated_media um
JOIN object_sources os
  ON os.object_id = um.object_id
 AND os.source_rank > 1;

ALTER TABLE event_media
    ALTER COLUMN source_id SET NOT NULL;

ALTER TABLE event_media
    ADD CONSTRAINT event_media_pkey PRIMARY KEY (source_id, object_id, media_id);

ALTER TABLE geo.features
    VALIDATE CONSTRAINT chk_geo_features_payload_source_matches;

ALTER TABLE geo.features
    VALIDATE CONSTRAINT chk_geo_features_payload_object_id_matches;
