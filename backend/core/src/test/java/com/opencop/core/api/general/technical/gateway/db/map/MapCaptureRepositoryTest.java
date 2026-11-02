// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.api.general.business.model.map.MapView;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapCaptureRepositoryTest {

  @Mock private MapCaptureJpaRepository repo;
  @Captor private ArgumentCaptor<MapCaptureEntity> entityCaptor;

  private MapCaptureRepository service;

  @BeforeEach
  void setUp() {
    service = new MapCaptureRepository(repo);
  }

  @Test
  void findById_returnsMappedCapture() throws PersistenceException {
    var id = UUID.randomUUID();
    var entity = entity(id, UUID.randomUUID(), "Home", 1.0, 2.0, 3.0, 4.0, 5.0);
    when(repo.findById(id)).thenReturn(Optional.of(entity));

    var result = service.findById(id);

    assertEquals(id, result.id());
    assertEquals("Home", result.name());
    assertEquals(1.0, result.mapView().bearing());
    verify(repo).findById(id);
  }

  @Test
  void findById_whenMissing_throwsPersistenceException() {
    var id = UUID.randomUUID();
    when(repo.findById(id)).thenReturn(Optional.empty());

    var error = assertThrows(PersistenceException.class, () -> service.findById(id));

    assertEquals("Not Found", error.getMessage());
    verify(repo).findById(id);
  }

  @Test
  void findAll_returnsMappedList() throws PersistenceException {
    var userId = UUID.randomUUID();
    when(repo.findByUserId(userId))
        .thenReturn(
            List.of(
                entity(UUID.randomUUID(), userId, "First", 1.0, 2.0, 3.0, 4.0, 5.0),
                entity(UUID.randomUUID(), userId, "Second", 6.0, 7.0, 8.0, 9.0, 10.0)));

    var result = service.findAll(userId);

    assertEquals(2, result.size());
    assertEquals("First", result.get(0).name());
    assertEquals("Second", result.get(1).name());
    verify(repo).findByUserId(userId);
  }

  @Test
  void create_whenValid_persistsMappedEntity() throws PersistenceException {
    var userId = UUID.randomUUID();
    var createdId = UUID.randomUUID();
    var capture =
        new MapCapture(null, "Pinned", new MapView(11.0, 12.0, 13.0, 14.0, 15.0), null, null);
    when(repo.save(any(MapCaptureEntity.class)))
        .thenAnswer(
            invocation -> {
              var saved = invocation.getArgument(0, MapCaptureEntity.class);
              saved.setId(createdId);
              return saved;
            });

    var result = service.create(userId, capture);

    verify(repo).save(entityCaptor.capture());
    var saved = entityCaptor.getValue();
    assertEquals(userId, saved.getUserId());
    assertEquals("Pinned", saved.getName());
    assertEquals(11.0, saved.getBearing());
    assertEquals(12.0, saved.getLatitude());
    assertEquals(createdId, result.id());
  }

  @Test
  void create_whenCaptureNull_throwsBadRequest() {
    var error =
        assertThrows(
            MapCapturePersistenceException.BadRequest.class,
            () -> service.create(UUID.randomUUID(), null));

    assertEquals("Map capture name and mapView are required", error.getMessage());
    verify(repo, never()).save(any(MapCaptureEntity.class));
  }

  @Test
  void create_whenNameBlank_throwsBadRequest() {
    var capture = new MapCapture(null, " ", new MapView(1.0, 2.0, 3.0, 4.0, 5.0), null, null);

    var error =
        assertThrows(
            MapCapturePersistenceException.BadRequest.class,
            () -> service.create(UUID.randomUUID(), capture));

    assertEquals("Map capture name and mapView are required", error.getMessage());
    verify(repo, never()).save(any(MapCaptureEntity.class));
  }

  @Test
  void patch_whenCaptureExists_appliesPatchAndSaves() throws PersistenceException {
    var captureId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var entity = entity(captureId, userId, "Before", 1.0, 2.0, 3.0, 4.0, 5.0);
    var patch =
        new MapCapturePatch(
            Optional.of("After"), Optional.of(new MapView(6.0, 7.0, 8.0, 9.0, 10.0)));
    when(repo.findById(captureId)).thenReturn(Optional.of(entity));
    when(repo.save(entity)).thenReturn(entity);

    var result = service.patch(captureId, patch, userId);

    assertEquals("After", entity.getName());
    assertEquals(6.0, entity.getBearing());
    assertEquals("After", result.name());
    assertEquals(6.0, result.mapView().bearing());
    verify(repo).findById(captureId);
    verify(repo).save(entity);
  }

  @Test
  void patch_whenMissing_throwsNotFound() {
    var captureId = UUID.randomUUID();
    when(repo.findById(captureId)).thenReturn(Optional.empty());

    var error =
        assertThrows(
            MapCapturePersistenceException.NotFound.class,
            () ->
                service.patch(
                    captureId,
                    new MapCapturePatch(Optional.empty(), Optional.empty()),
                    UUID.randomUUID()));

    assertTrue(error.getMessage().contains(captureId.toString()));
    verify(repo).findById(captureId);
  }

  @Test
  void patch_whenUserDoesNotOwnCapture_throwsPersistenceException() {
    var captureId = UUID.randomUUID();
    var entity = entity(captureId, UUID.randomUUID(), "Before", 1.0, 2.0, 3.0, 4.0, 5.0);
    when(repo.findById(captureId)).thenReturn(Optional.of(entity));

    var error =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patch(
                    captureId,
                    new MapCapturePatch(Optional.of("After"), Optional.empty()),
                    UUID.randomUUID()));

    assertEquals("Tried patching a capture that the user does not own", error.getMessage());
    verify(repo).findById(captureId);
    verify(repo, never()).save(any(MapCaptureEntity.class));
  }

  @Test
  void delete_whenExistsAndUserOwnsCapture_deletesById() throws PersistenceException {
    var captureId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    when(repo.findById(captureId))
        .thenReturn(Optional.of(entity(captureId, userId, "Home", 1.0, 2.0, 3.0, 4.0, 5.0)));

    service.delete(captureId, userId);

    verify(repo).findById(captureId);
    verify(repo).deleteById(captureId);
  }

  @Test
  void delete_whenMissing_throwsNotFound() {
    var captureId = UUID.randomUUID();
    when(repo.findById(captureId)).thenReturn(Optional.empty());

    var error =
        assertThrows(
            MapCapturePersistenceException.NotFound.class,
            () -> service.delete(captureId, UUID.randomUUID()));

    assertInstanceOf(MapCapturePersistenceException.NotFound.class, error);
    verify(repo).findById(captureId);
    verify(repo, never()).deleteById(captureId);
  }

  @Test
  void delete_whenUserDoesNotOwnCapture_throwsPersistenceException() {
    var captureId = UUID.randomUUID();
    when(repo.findById(captureId))
        .thenReturn(
            Optional.of(entity(captureId, UUID.randomUUID(), "Pinned", 1.0, 2.0, 3.0, 4.0, 5.0)));

    var error =
        assertThrows(
            PersistenceException.class, () -> service.delete(captureId, UUID.randomUUID()));

    assertEquals("Tried deleting a capture that the user does not own", error.getMessage());
    verify(repo).findById(captureId);
    verify(repo, never()).deleteById(captureId);
  }

  private static MapCaptureEntity entity(
      UUID id,
      UUID userId,
      String name,
      double bearing,
      double latitude,
      double longitude,
      double pitch,
      double zoom) {
    var entity = new MapCaptureEntity();
    entity.setId(id);
    entity.setUserId(userId);
    entity.setName(name);
    entity.setBearing(bearing);
    entity.setLatitude(latitude);
    entity.setLongitude(longitude);
    entity.setPitch(pitch);
    entity.setZoom(zoom);
    return entity;
  }
}
