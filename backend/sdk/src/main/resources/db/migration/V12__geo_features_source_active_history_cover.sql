${geoFeaturesHistoryChunkIndexSql}

CREATE INDEX geofeatures_source_active_history_cover_idx
    ON geo.features (
                     source,
                     object_id,
                     stale_at,
                     COALESCE(start_at, created_at) DESC,
                     created_at DESC,
                     id DESC
        )
    INCLUDE (start_at);

CREATE TABLE geo.feature_history_checkpoint (
                                                source        UUID NOT NULL,
                                                object_id     VARCHAR(255) NOT NULL,
                                                resolution    VARCHAR(32) NOT NULL,
                                                checkpoint_at timestamptz NOT NULL,
                                                event_id      UUID NOT NULL,
                                                playback_at   timestamptz NOT NULL,
                                                created_at    timestamptz NOT NULL,
                                                stale_at      timestamptz,
                                                PRIMARY KEY (source, object_id, resolution, checkpoint_at)
);

CREATE INDEX geo_feature_history_checkpoint_lookup_idx
    ON geo.feature_history_checkpoint (source, resolution, checkpoint_at, object_id, playback_at, event_id);

CREATE INDEX geo_feature_history_checkpoint_event_idx
    ON geo.feature_history_checkpoint (event_id);

CREATE INDEX geo_feature_history_checkpoint_resolution_lookup_idx
    ON geo.feature_history_checkpoint (source, resolution, checkpoint_at DESC, object_id, event_id);

CREATE TABLE geo.feature_latest_active_helper (
                                                  source      UUID NOT NULL,
                                                  object_id   VARCHAR(255) NOT NULL,
                                                  event_id    UUID NOT NULL,
                                                  playback_at timestamptz NOT NULL,
                                                  created_at  timestamptz NOT NULL,
                                                  stale_at    timestamptz,
                                                  PRIMARY KEY (source, object_id)
);

INSERT INTO geo.feature_latest_active_helper (
    source,
    object_id,
    event_id,
    playback_at,
    created_at,
    stale_at
)
SELECT DISTINCT ON (features.source, features.object_id)
    features.source,
    features.object_id,
    features.id,
    COALESCE(features.start_at, features.created_at) AS playback_at,
    features.created_at,
    features.stale_at
FROM geo.features features
WHERE features.object_id IS NOT NULL
ORDER BY
    features.source,
    features.object_id,
    COALESCE(features.start_at, features.created_at) DESC,
    features.created_at DESC,
    features.id DESC;

CREATE INDEX geo_feature_latest_active_helper_lookup_idx
    ON geo.feature_latest_active_helper (source, playback_at DESC, object_id, event_id);
