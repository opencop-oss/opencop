-- Wrap legacy flat tile configs into the new MapTileConfig envelope {type, extension, tile}.
-- Old rows stored tile properties (kind, source, etc.) at the top level of config.
-- New code expects them nested under config.tile, with type and extension at the top level.
UPDATE map_tile
SET config = jsonb_build_object(
    'type',      '"BASEMAP"'::jsonb,
    'extension', NULL,
    'tile',      config - 'type' - 'extension'
)
WHERE config -> 'tile' IS NULL;
