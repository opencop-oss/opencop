// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapView;
import java.util.UUID;

public final class MapCaptureMapper {

  private MapCaptureMapper() {}

  public static MapCapture toModel(MapCaptureEntity entity) {
    return new MapCapture(
        entity.getId(),
        entity.getName(),
        new MapView(
            entity.getBearing(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getPitch(),
            entity.getZoom()),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public static void applyCreate(MapCapture model, MapCaptureEntity entity, UUID userId) {
    entity.setUserId(userId);
    entity.setName(model.name());
    entity.setBearing(model.mapView().bearing());
    entity.setLatitude(model.mapView().latitude());
    entity.setLongitude(model.mapView().longitude());
    entity.setPitch(model.mapView().pitch());
    entity.setZoom(model.mapView().zoom());
  }

  public static void applyModel(MapCapture model, MapCaptureEntity entity) {
    if (model.id() != null) {
      entity.setId(model.id());
    }
    entity.setName(model.name());
    entity.setBearing(model.mapView().bearing());
    entity.setLatitude(model.mapView().latitude());
    entity.setLongitude(model.mapView().longitude());
    entity.setPitch(model.mapView().pitch());
    entity.setZoom(model.mapView().zoom());
  }

  public static void applyMapView(MapView mapView, MapCaptureEntity entity) {
    entity.setBearing(mapView.bearing());
    entity.setLatitude(mapView.latitude());
    entity.setLongitude(mapView.longitude());
    entity.setPitch(mapView.pitch());
    entity.setZoom(mapView.zoom());
  }
}
