-- Adds `first_emission_at` to the helper table so it can answer interval-overlap
-- queries like "was this entity alive at any point during [bucket_from, bucket_to)?"
-- The existing `playback_at` column tracks the entity's *latest* emission; without
-- a corresponding lower bound we cannot distinguish "entity emitted only after
-- bucket_to" from "entity emitted throughout the bucket and is still emitting."
--
-- Reader: PostGisRepository.buildSourceHistogramSql (the `ACTIVE` count). After
-- this migration the histogram's interval-overlap query is `helper.first_emission_at
-- < bucket_to AND (helper.stale_at IS NULL OR helper.stale_at > bucket_from)`.
-- Writer: PostGisService.HELPER_SQL upsert.

ALTER TABLE geo.feature_latest_active_helper
    ADD COLUMN first_emission_at timestamptz;

-- Backfill from the earliest emission per (source, object_id) in geo.features.
-- One-time scan; same shape as the V10 helper-table backfill.
UPDATE geo.feature_latest_active_helper helper
SET first_emission_at = sub.first_emission_at
FROM (
    SELECT
        source,
        object_id,
        MIN(COALESCE(start_at, created_at)) AS first_emission_at
    FROM geo.features
    WHERE object_id IS NOT NULL
    GROUP BY source, object_id
) sub
WHERE helper.source = sub.source
  AND helper.object_id = sub.object_id;

ALTER TABLE geo.feature_latest_active_helper
    ALTER COLUMN first_emission_at SET NOT NULL;

-- Supports the histogram's interval-overlap join:
--   ON helper.source = :source
--  AND helper.first_emission_at < bucket_to
--  AND (helper.stale_at IS NULL OR helper.stale_at > bucket_from)
-- INCLUDE (playback_at, event_id) enables index-only scans for the
-- snapshot fast-path predicate that classifies helper rows as stable-winners
-- (playback_at < chunk.from) or residuals (playback_at >= chunk.from):
--   helper.source = :sourceId
--   AND helper.first_emission_at < :to
--   AND (helper.stale_at IS NULL OR helper.stale_at > :from)
--   AND helper.playback_at < :from
-- Both INCLUDE columns (playback_at, event_id) are read by the fast-path
-- dispatcher without touching the heap. stale_at is a key column and is
-- already covered by the index without being repeated in INCLUDE.
CREATE INDEX geo_feature_latest_active_helper_lifespan_idx
    ON geo.feature_latest_active_helper (source, first_emission_at, stale_at)
    INCLUDE (playback_at, event_id);
