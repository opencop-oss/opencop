// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.geojson.Feature;
import org.geojson.Point;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class EventFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public EventFixtures(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM geo.feature_latest_active_helper");
    jdbcTemplate.update("DELETE FROM geo.features");
  }

  public void insertFeature(UUID id, String objectId, UUID source, Instant createdAt) {
    insertFeature(id, objectId, source, createdAt, createdAt);
  }

  public void insertFeature(
      UUID id, String objectId, UUID source, Instant createdAt, Instant startAt) {
    insertFeature(id, objectId, source, createdAt, startAt, 0, 0);
  }

  public void insertFeature(
      UUID id,
      String objectId,
      UUID source,
      Instant createdAt,
      Instant startAt,
      double lon,
      double lat) {
    var properties = new OpenCopProperties();
    properties.setObjectId(objectId);
    properties.setSource(source);
    Feature feature = OpenCopPropertiesParser.writeToFeature(properties, new Point(lon, lat));

    try {
      jdbcTemplate.update(
          "INSERT INTO geo.features (id, source, object_id, created_at, start_at, geom, payload) "
              + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
          id,
          source,
          objectId,
          Timestamp.from(createdAt),
          startAt == null ? null : Timestamp.from(startAt),
          "POINT Z (" + lon + " " + lat + " 0)",
          objectMapper.writeValueAsString(feature));

      Timestamp emissionAt = startAt == null ? Timestamp.from(createdAt) : Timestamp.from(startAt);
      jdbcTemplate.update(
          """
          INSERT INTO geo.feature_latest_active_helper
            (source, object_id, event_id, playback_at, first_emission_at, created_at, stale_at)
          VALUES (?, ?, ?, ?, ?, ?, NULL)
          ON CONFLICT (source, object_id) DO UPDATE SET
            event_id = EXCLUDED.event_id,
            playback_at = GREATEST(geo.feature_latest_active_helper.playback_at, EXCLUDED.playback_at),
            first_emission_at = LEAST(geo.feature_latest_active_helper.first_emission_at, EXCLUDED.first_emission_at),
            created_at = EXCLUDED.created_at,
            stale_at = EXCLUDED.stale_at
          """,
          source,
          objectId,
          id,
          emissionAt,
          emissionAt,
          Timestamp.from(createdAt));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to insert event fixture", e);
    }
  }
}
