// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FlywayGeoFeaturesPlaceholders {

  public static final String POSTGIS_EXTENSION_SQL_KEY = "postgisExtensionSql";
  public static final String GEO_FEATURES_GEOM_TYPE_KEY = "geoFeaturesGeomType";
  public static final String GEO_FEATURES_GEOM_INDEX_SQL_KEY = "geoFeaturesGeomIndexSql";
  public static final String GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL_KEY =
      "geoFeaturesHistoryChunkIndexSql";
  public static final String GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL_KEY =
      "geoFeaturesActiveHistoryLookupIndexSql";

  public static final String POSTGIS_EXTENSION_SQL = "SELECT 1;";
  public static final String GEO_FEATURES_GEOM_TYPE = "text NOT NULL";
  public static final String GEO_FEATURES_GEOM_INDEX_SQL =
      "CREATE INDEX geofeatures_geom_idx ON geo.features (geom);";
  public static final String GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL =
      "CREATE INDEX geofeatures_history_chunk_idx ON geo.features (source, COALESCE(start_at, created_at), object_id, id);";
  public static final String GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL =
      "CREATE INDEX geofeatures_active_history_lookup_idx ON geo.features (source, object_id, COALESCE(start_at, created_at) DESC, created_at DESC, id DESC);";

  private FlywayGeoFeaturesPlaceholders() {}

  public static Map<String, String> defaultPlaceholders() {
    Map<String, String> placeholders = new LinkedHashMap<>();
    placeholders.put(POSTGIS_EXTENSION_SQL_KEY, POSTGIS_EXTENSION_SQL);
    placeholders.put(GEO_FEATURES_GEOM_TYPE_KEY, GEO_FEATURES_GEOM_TYPE);
    placeholders.put(GEO_FEATURES_GEOM_INDEX_SQL_KEY, GEO_FEATURES_GEOM_INDEX_SQL);
    placeholders.put(
        GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL_KEY, GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL);
    placeholders.put(
        GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL_KEY,
        GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL);
    return Map.copyOf(placeholders);
  }
}
