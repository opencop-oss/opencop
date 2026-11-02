CREATE INDEX IF NOT EXISTS geofeatures_source_object_playback_latest_idx
ON geo.features (
  source,
  object_id,
  (COALESCE(start_at, created_at)) DESC,
  created_at DESC,
  id DESC
)
INCLUDE (stale_at, start_at);
