// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapView;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureEntity;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.sdk.component.TestFixture;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class MapCaptureFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;
  private final MapCaptureJpaRepository mapCaptureRepository;

  public MapCaptureFixtures(
      JdbcTemplate jdbcTemplate, MapCaptureJpaRepository mapCaptureRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.mapCaptureRepository = mapCaptureRepository;
  }

  @Override
  public void reset() {
    mapCaptureRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM iam.layer WHERE archived_at IS NOT NULL");
    jdbcTemplate.update("DELETE FROM profiles.data_source WHERE archived_at IS NOT NULL");
    jdbcTemplate.update("delete from opencop_user");
  }

  public void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        insert into opencop_user (
          id,
          idp_user_id,
          idp_username,
          idp_issuer,
          username,
          email,
          first_name,
          last_name,
          display_name,
          active,
          administrator
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "http://localhost/test-issuer",
        "user-" + userId,
        "user-" + userId + "@example.com",
        "Test",
        "User",
        "Test User",
        true,
        false);
  }

  public UUID persistMapCapture(UUID userId, String name, MapView mapView) {
    var entity = new MapCaptureEntity();
    entity.setUserId(userId);
    entity.setName(name);
    entity.setBearing(mapView.bearing());
    entity.setLatitude(mapView.latitude());
    entity.setLongitude(mapView.longitude());
    entity.setPitch(mapView.pitch());
    entity.setZoom(mapView.zoom());
    return mapCaptureRepository.save(entity).getId();
  }

  public long count() {
    return mapCaptureRepository.count();
  }

  public static MapCapture defaultCreate(String name) {
    return new MapCapture(null, name, defaultMapView(), null, null);
  }

  public static MapView defaultMapView() {
    return new MapView(11.5, 39.9526, -75.1652, 38.0, 13.25);
  }

  public static MapView alternateMapView() {
    return new MapView(22.0, 39.9526, -75.1652, 47.5, 9.75);
  }
}
