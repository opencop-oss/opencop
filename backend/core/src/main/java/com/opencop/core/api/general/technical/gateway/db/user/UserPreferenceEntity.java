// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "opencop_user_preference")
public class UserPreferenceEntity {

  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "locale", nullable = false)
  private String locale = "en";

  @Enumerated(EnumType.STRING)
  @Column(name = "measurement_system", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private MeasurementSystem measurementSystem = MeasurementSystem.METRIC;

  @Enumerated(EnumType.STRING)
  @Column(name = "temperature_unit", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private TemperatureUnit temperatureUnit = TemperatureUnit.CELSIUS;

  @Column(name = "timezone", nullable = false)
  private String timezone = "UTC";

  @Enumerated(EnumType.STRING)
  @Column(name = "zoom_behavior", nullable = false)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ZoomBehavior zoomBehavior = ZoomBehavior.CENTER;

  @Column(name = "avatar_media_id")
  private UUID avatarMediaId;

  @Column(name = "map_capture_id")
  private UUID mapCaptureId;

  @Column(name = "map_tile_id")
  private UUID mapTileId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public MeasurementSystem getMeasurementSystem() {
    return measurementSystem;
  }

  public void setMeasurementSystem(MeasurementSystem measurementSystem) {
    this.measurementSystem = measurementSystem;
  }

  public TemperatureUnit getTemperatureUnit() {
    return temperatureUnit;
  }

  public void setTemperatureUnit(TemperatureUnit temperatureUnit) {
    this.temperatureUnit = temperatureUnit;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public ZoomBehavior getZoomBehavior() {
    return zoomBehavior;
  }

  public void setZoomBehavior(ZoomBehavior zoomBehavior) {
    this.zoomBehavior = zoomBehavior;
  }

  public UUID getAvatarMediaId() {
    return avatarMediaId;
  }

  public void setAvatarMediaId(UUID avatarMediaId) {
    this.avatarMediaId = avatarMediaId;
  }

  public UUID getMapCaptureId() {
    return mapCaptureId;
  }

  public void setMapCaptureId(UUID mapCaptureId) {
    this.mapCaptureId = mapCaptureId;
  }

  public UUID getMapTileId() {
    return mapTileId;
  }

  public void setMapTileId(UUID mapTileId) {
    this.mapTileId = mapTileId;
  }
}
