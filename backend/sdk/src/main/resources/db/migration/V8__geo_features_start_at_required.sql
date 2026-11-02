-- Backfill: use created_at as best-effort approximation. For events ingested
-- before this migration, created_at is the DB insert time, not the event's
-- logical start time (opencop.timestampz.start). Histogram accuracy for
-- pre-migration data may be affected.
UPDATE geo.features
SET start_at = created_at
WHERE start_at IS NULL;

ALTER TABLE geo.features
    ALTER COLUMN start_at SET NOT NULL;

CREATE INDEX geofeatures_source_start_at
    ON geo.features (source, start_at);
