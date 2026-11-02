-- Imported images become their own element type. Until now an image placed on the map was stored as
-- a `symbol` element holding its image reference in `symbol`, the field that otherwise holds a
-- MIL-STD-2525 symbol code. Each such element is re-typed to `media-icon` with the reference moved,
-- unchanged, into `href` (a Core-stored image keeps its `#WxH` size fragment). The mapper target that
-- produced them is renamed to match.

-- A reference is an image, not a symbol code, when it takes a form that draws as an image, or when it
-- is a path or an image file name: a relative path that matched no bundled image. No symbol code
-- contains a path separator or ends in an image extension (the extensions are those Core extracts
-- from an imported archive).
CREATE FUNCTION pg_temp.is_media_icon_ref(ref text) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
  SELECT ref ~* '^(opencop:url:|https?://|data:)'
      OR ref ~ '[/\\]'
      OR ref ~* '\.(png|jpe?g|gif|bmp|webp|svg)$'
$$;

CREATE FUNCTION pg_temp.to_media_icon(element jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT (element - 'symbol') || jsonb_build_object('type', 'media-icon', 'href', element ->> 'symbol')
$$;

-- The sources whose features were re-typed. The live map is served from a Redis cache that still
-- holds the old elements and that this migration cannot reach; Core's startup repair rebuilds the
-- cache of each source listed here and removes its row.
CREATE TABLE geo.media_icon_cache_repair (
    source_id uuid PRIMARY KEY
);

-- Only the dynamic mapper's icon target ever produced an image symbol: a KML/KMZ import into a layer,
-- or a data source whose mapper maps a field to that target. Every other row is a symbol code, and
-- those are nearly all of `geo.features` (track history), so the scan is confined to the sources that
-- can hold an image instead of reading the whole table. A data source counts when any stored version
-- of its mapper, or of the mapper its legacy data profile names, uses the icon target; this runs
-- before that target is renamed below.
CREATE TEMP TABLE media_icon_candidate_source ON COMMIT DROP AS
SELECT id AS source_id FROM iam.layer
UNION
SELECT ds.id
  FROM profiles.data_source ds
  LEFT JOIN profiles.data_profile dp ON dp.id = ds.data_profile_id
 WHERE EXISTS (SELECT 1
                 FROM profiles.data_mapper m
                WHERE m.id IN (ds.ingress_mapper_id, dp.mapper_id)
                  AND m.mapper::text LIKE '%"ELEMENT_SYMBOL_ICON"%');

-- The dynamic mapper stamps no feature kind, and a user's edit of an imported image is re-published
-- as a broadcast, which stamps `opencop.internal`. Integration decoders stamp their own kind (TAK
-- writes `tak.cot.v1`, whose symbol is the sender's own __milsym/__milicon value); those rows never
-- held an imported image and are left as they are, whatever their symbol holds.
--
-- Each source is converted on its own so that a `source` index bounds the read to that source's rows.
-- The kind and element-type tests are written so no index can serve them: `type = symbol` matches
-- nearly every row and `feature_kind IS NULL` about half, so a plan led by the payload GIN index or
-- the feature_kind index would read most of the table for every source. Sequential scans are off for
-- this transaction for the same reason. Features carry their element under `properties.opencop`;
-- older rows may also, or only, carry it under a top-level `opencop` node, and both are re-typed.
SET LOCAL enable_seqscan = off;

DO $$
DECLARE
    candidate uuid;
BEGIN
    FOR candidate IN SELECT source_id FROM media_icon_candidate_source LOOP
        WITH retyped AS (
            UPDATE geo.features
               SET payload = jsonb_set(payload, '{properties,opencop,element}',
                                       pg_temp.to_media_icon(payload #> '{properties,opencop,element}'))
             WHERE source = candidate
               AND coalesce(feature_kind, '') IN ('', 'opencop.internal')
               AND payload #>> '{properties,opencop,element,type}' = 'symbol'
               AND pg_temp.is_media_icon_ref(payload #>> '{properties,opencop,element,symbol}')
            RETURNING source
        )
        INSERT INTO geo.media_icon_cache_repair (source_id)
        SELECT DISTINCT source FROM retyped
        ON CONFLICT DO NOTHING;

        WITH retyped AS (
            UPDATE geo.features
               SET payload = jsonb_set(payload, '{opencop,element}',
                                       pg_temp.to_media_icon(payload #> '{opencop,element}'))
             WHERE source = candidate
               AND coalesce(feature_kind, '') IN ('', 'opencop.internal')
               AND payload #>> '{opencop,element,type}' = 'symbol'
               AND pg_temp.is_media_icon_ref(payload #>> '{opencop,element,symbol}')
            RETURNING source
        )
        INSERT INTO geo.media_icon_cache_repair (source_id)
        SELECT DISTINCT source FROM retyped
        ON CONFLICT DO NOTHING;
    END LOOP;
END
$$;

-- A mapper rule names its target by the enum constant, and an unknown name fails the whole mapper
-- on load, so every stored version of every mapper is rewritten.
UPDATE profiles.data_mapper
   SET mapper = replace(mapper::text, '"ELEMENT_SYMBOL_ICON"', '"ELEMENT_ICON_LOCATION"')::jsonb
 WHERE mapper::text LIKE '%"ELEMENT_SYMBOL_ICON"%';
