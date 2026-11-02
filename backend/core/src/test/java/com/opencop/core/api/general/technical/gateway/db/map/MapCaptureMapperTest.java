// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapCaptureMapperTest {

  @Test
  void toModel_mapsEntityToModel() {
    var entity = new MapCaptureEntity();
    var id = UUID.randomUUID();
    var createdAt = Instant.parse("2026-03-01T10:15:30Z");
    var updatedAt = Instant.parse("2026-03-02T11:16:31Z");
    entity.setId(id);
    entity.setName("Home");
    entity.setBearing(10.5);
    entity.setLatitude(45.5);
    entity.setLongitude(-73.5);
    entity.setPitch(15.5);
    entity.setZoom(12.5);
    TestMapCaptureEntityTimestamps.setCreatedAt(entity, createdAt);
    TestMapCaptureEntityTimestamps.setUpdatedAt(entity, updatedAt);

    var model = MapCaptureMapper.toModel(entity);

    assertEquals(id, model.id());
    assertEquals("Home", model.name());
    assertEquals(10.5, model.mapView().bearing());
    assertEquals(45.5, model.mapView().latitude());
    assertEquals(-73.5, model.mapView().longitude());
    assertEquals(15.5, model.mapView().pitch());
    assertEquals(12.5, model.mapView().zoom());
    assertEquals(createdAt, model.createdAt());
    assertEquals(updatedAt, model.updatedAt());
  }

  @Test
  void applyCreate_mapsModelAndUserIdToEntity() {
    var userId = UUID.randomUUID();
    var model = new MapCapture(null, "HQ", new MapView(1.25, 2.5, 3.75, 4.125, 5.875), null, null);
    var entity = new MapCaptureEntity();

    MapCaptureMapper.applyCreate(model, entity, userId);

    assertEquals(userId, entity.getUserId());
    assertEquals("HQ", entity.getName());
    assertEquals(1.25, entity.getBearing());
    assertEquals(2.5, entity.getLatitude());
    assertEquals(3.75, entity.getLongitude());
    assertEquals(4.125, entity.getPitch());
    assertEquals(5.875, entity.getZoom());
  }

  @Test
  void applyModel_withNullId_doesNotOverwriteEntityId() {
    var existingId = UUID.randomUUID();
    var model =
        new MapCapture(null, "Renamed", new MapView(6.25, 7.5, 8.75, 9.125, 10.875), null, null);
    var entity = new MapCaptureEntity();
    entity.setId(existingId);

    MapCaptureMapper.applyModel(model, entity);

    assertEquals(existingId, entity.getId());
    assertEquals("Renamed", entity.getName());
    assertEquals(6.25, entity.getBearing());
    assertEquals(7.5, entity.getLatitude());
    assertEquals(8.75, entity.getLongitude());
    assertEquals(9.125, entity.getPitch());
    assertEquals(10.875, entity.getZoom());
  }

  @Test
  void applyModel_withId_setsEntityId() {
    var modelId = UUID.randomUUID();
    var model =
        new MapCapture(
            modelId, "Pinned", new MapView(11.25, 12.5, 13.75, 14.125, 15.875), null, null);
    var entity = new MapCaptureEntity();

    MapCaptureMapper.applyModel(model, entity);

    assertEquals(modelId, entity.getId());
  }

  @Test
  void applyMapView_updatesOnlyMapViewFields() {
    var entity = new MapCaptureEntity();
    entity.setName("Kept");
    entity.setId(UUID.randomUUID());
    var mapView = new MapView(16.25, 17.5, 18.75, 19.125, 20.875);

    MapCaptureMapper.applyMapView(mapView, entity);

    assertEquals(16.25, entity.getBearing());
    assertEquals(17.5, entity.getLatitude());
    assertEquals(18.75, entity.getLongitude());
    assertEquals(19.125, entity.getPitch());
    assertEquals(20.875, entity.getZoom());
    assertEquals("Kept", entity.getName());
    assertNull(entity.getUserId());
  }

  private static final class TestMapCaptureEntityTimestamps {
    static void setCreatedAt(MapCaptureEntity entity, Instant createdAt) {
      trySetField(entity, "createdAt", createdAt);
    }

    static void setUpdatedAt(MapCaptureEntity entity, Instant updatedAt) {
      trySetField(entity, "updatedAt", updatedAt);
    }

    private static void trySetField(MapCaptureEntity entity, String name, Instant value) {
      try {
        var field = MapCaptureEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("Unable to set %s for test".formatted(name), e);
      }
    }
  }
}
