// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureEntity;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class JpaUserPreferenceRepositoryTest {

  @Mock private UserPreferenceJpaRepository userPreferenceRepo;
  @Mock private MapCaptureJpaRepository mapCaptureRepo;
  @Mock private MapTileService mapTileService;
  @Mock private InternalUserRepository internalUserRepo;
  @Captor private ArgumentCaptor<UserPreferenceEntity> entityCaptor;

  private JpaUserPreferenceRepository repository;

  @BeforeEach
  void setUp() {
    repository =
        new JpaUserPreferenceRepository(
            userPreferenceRepo, mapCaptureRepo, mapTileService, internalUserRepo);
  }

  @Test
  void findByUserId_whenMissing_returnsDefaultPreference() {
    var userId = UUID.randomUUID();
    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.empty());
    when(internalUserRepo.findById(userId)).thenReturn(Optional.empty());

    var result = repository.findByUserId(userId);

    assertNull(result.username());
    assertEquals("en", result.locale());
    assertEquals(MeasurementSystem.METRIC, result.measurementSystem());
    assertEquals(TemperatureUnit.CELSIUS, result.temperatureUnit());
    assertEquals("UTC", result.timezone());
    assertEquals(ZoomBehavior.CENTER, result.zoomBehavior());
    assertNull(result.avatarMediaId());
    assertNull(result.avatar());
    assertNull(result.initialView());
    assertNull(result.defaultTile());
    verify(userPreferenceRepo).findByUserId(userId);
    verify(mapCaptureRepo, never()).findById(any(UUID.class));
    verify(mapTileService, never()).getOneSanitized(any(UUID.class));
  }

  @Test
  void findByUserId_whenEntityExists_mapsCaptureAndDefaultTile() {
    var userId = UUID.randomUUID();
    var captureId = UUID.randomUUID();
    var tileId = UUID.randomUUID();
    var avatarId = UUID.randomUUID();
    var user = new InternalUserEntity();
    user.setId(userId);
    user.setUsername("preferred-user");

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("fr");
    entity.setMeasurementSystem(MeasurementSystem.IMPERIAL);
    entity.setTemperatureUnit(TemperatureUnit.FAHRENHEIT);
    entity.setTimezone("America/New_York");
    entity.setZoomBehavior(ZoomBehavior.MOUSE);
    entity.setAvatarMediaId(avatarId);
    entity.setMapCaptureId(captureId);
    entity.setMapTileId(tileId);

    var captureEntity = new MapCaptureEntity();
    captureEntity.setId(captureId);
    captureEntity.setName("Start");
    captureEntity.setBearing(1.0);
    captureEntity.setLatitude(2.0);
    captureEntity.setLongitude(3.0);
    captureEntity.setPitch(4.0);
    captureEntity.setZoom(5.0);

    var tile = new MapTile();
    tile.setId(tileId);

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(mapCaptureRepo.findById(captureId)).thenReturn(Optional.of(captureEntity));
    when(mapTileService.getOneSanitized(tileId)).thenReturn(Optional.of(tile));
    when(internalUserRepo.findById(userId)).thenReturn(Optional.of(user));

    var result = repository.findByUserId(userId);

    assertEquals("preferred-user", result.username());
    assertEquals("fr", result.locale());
    assertEquals(MeasurementSystem.IMPERIAL, result.measurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, result.temperatureUnit());
    assertEquals("America/New_York", result.timezone());
    assertEquals(ZoomBehavior.MOUSE, result.zoomBehavior());
    assertEquals(avatarId, result.avatarMediaId());
    assertNull(result.avatar()); // enriched at endpoint layer, not in repository
    assertEquals(captureId, result.initialView().id());
    assertSame(tile, result.defaultTile());
  }

  @Test
  void create_setsDefaultValuesAndSaves() {
    var userId = UUID.randomUUID();
    when(internalUserRepo.findById(userId)).thenReturn(Optional.empty());
    when(userPreferenceRepo.save(any(UserPreferenceEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceEntity.class));

    var result = repository.create(userId);

    verify(userPreferenceRepo).save(entityCaptor.capture());
    var saved = entityCaptor.getValue();
    assertEquals(userId, saved.getUserId());
    assertEquals("en", saved.getLocale());
    assertEquals(MeasurementSystem.METRIC, saved.getMeasurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, saved.getTemperatureUnit());
    assertEquals("UTC", saved.getTimezone());
    assertEquals(ZoomBehavior.CENTER, saved.getZoomBehavior());
    assertNull(saved.getAvatarMediaId());
    assertNull(saved.getMapTileId());
    assertNull(result.username());
    assertEquals("en", result.locale());
    assertEquals(MeasurementSystem.METRIC, result.measurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, result.temperatureUnit());
    assertEquals("UTC", result.timezone());
    assertEquals(ZoomBehavior.CENTER, result.zoomBehavior());
  }

  @Test
  void patch_whenExistingEntity_updatesProvidedFields() throws PersistenceException {
    var userId = UUID.randomUUID();
    var captureId = UUID.randomUUID();
    var tileId = UUID.randomUUID();
    var user = new InternalUserEntity();
    user.setId(userId);
    user.setUsername("preferred-user");

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");
    entity.setMeasurementSystem(MeasurementSystem.METRIC);
    entity.setTemperatureUnit(TemperatureUnit.CELSIUS);
    entity.setTimezone("UTC");
    entity.setZoomBehavior(ZoomBehavior.CENTER);

    var patch =
        new UserPreferencePatch(
            Optional.of("updated-user"),
            Optional.of("es"),
            Optional.of(MeasurementSystem.IMPERIAL),
            Optional.of(TemperatureUnit.FAHRENHEIT),
            Optional.of("Europe/Paris"),
            Optional.of(ZoomBehavior.MOUSE),
            Optional.of(captureId),
            Optional.of(tileId),
            null);

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(userPreferenceRepo.save(entity)).thenReturn(entity);
    when(mapCaptureRepo.findById(captureId)).thenReturn(Optional.empty());
    when(mapTileService.getOneSanitized(tileId)).thenReturn(Optional.empty());
    when(internalUserRepo.findById(userId)).thenReturn(Optional.of(user));
    when(internalUserRepo.save(user)).thenReturn(user);

    var result = repository.patch(userId, patch);

    assertEquals("updated-user", user.getUsername());
    assertEquals("es", entity.getLocale());
    assertEquals(MeasurementSystem.IMPERIAL, entity.getMeasurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, entity.getTemperatureUnit());
    assertEquals("Europe/Paris", entity.getTimezone());
    assertEquals(ZoomBehavior.MOUSE, entity.getZoomBehavior());
    assertEquals(captureId, entity.getMapCaptureId());
    assertEquals(tileId, entity.getMapTileId());
    assertEquals("updated-user", result.username());
    assertEquals("es", result.locale());
    assertEquals(MeasurementSystem.IMPERIAL, result.measurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, result.temperatureUnit());
    assertEquals("Europe/Paris", result.timezone());
    assertEquals(ZoomBehavior.MOUSE, result.zoomBehavior());
  }

  @Test
  void patch_whenEntityMissing_createsWithDefaultLocale() throws PersistenceException {
    var userId = UUID.randomUUID();
    var tileId = UUID.randomUUID();
    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(tileId),
            null);

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.empty());
    when(internalUserRepo.findById(userId)).thenReturn(Optional.empty());
    when(userPreferenceRepo.save(any(UserPreferenceEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceEntity.class));

    var result = repository.patch(userId, patch);

    verify(userPreferenceRepo).save(entityCaptor.capture());
    var saved = entityCaptor.getValue();
    assertEquals(userId, saved.getUserId());
    assertEquals("en", saved.getLocale());
    assertEquals(tileId, saved.getMapTileId());
    assertEquals(MeasurementSystem.METRIC, saved.getMeasurementSystem());
    assertEquals(TemperatureUnit.CELSIUS, saved.getTemperatureUnit());
    assertNull(saved.getMapCaptureId());
    assertNull(result.username());
    assertEquals("en", result.locale());
    assertEquals(MeasurementSystem.METRIC, result.measurementSystem());
    assertEquals(TemperatureUnit.CELSIUS, result.temperatureUnit());
    assertEquals("UTC", result.timezone());
    assertEquals(ZoomBehavior.CENTER, result.zoomBehavior());
  }

  @Test
  void patch_whenUsernameAlreadyTaken_throwsConflictAndDoesNotPersist() {
    var userId = UUID.randomUUID();
    var otherUserId = UUID.randomUUID();

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");

    var existingUser = new InternalUserEntity();
    existingUser.setId(otherUserId);
    existingUser.setUsername("taken-user");

    var patch =
        new UserPreferencePatch(
            Optional.of("taken-user"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(internalUserRepo.findByUsername("taken-user")).thenReturn(Optional.of(existingUser));

    var error = assertThrows(GlobalOpenCopException.class, () -> repository.patch(userId, patch));

    assertEquals("Username already taken: taken-user", error.getMessage());
    assertEquals(HttpStatus.CONFLICT, error.getHttpStatus());
    verify(userPreferenceRepo, never()).save(any(UserPreferenceEntity.class));
    verify(internalUserRepo, never()).findById(userId);
    verify(internalUserRepo, never()).save(any(InternalUserEntity.class));
  }

  @Test
  void patch_withNullAvatarMediaId_doesNotTouchExistingAvatar() throws PersistenceException {
    var userId = UUID.randomUUID();
    var existingAvatarId = UUID.randomUUID();

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");
    entity.setAvatarMediaId(existingAvatarId);
    var user = new InternalUserEntity();
    user.setId(userId);
    user.setUsername("preferred-user");

    // null avatarMediaId (raw) = "don't touch"
    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(userPreferenceRepo.save(entity)).thenReturn(entity);
    when(internalUserRepo.findById(userId)).thenReturn(Optional.of(user));

    var result = repository.patch(userId, patch);

    assertEquals("preferred-user", result.username());
    assertEquals("fr", entity.getLocale());
    // Avatar must be unchanged
    assertEquals(existingAvatarId, entity.getAvatarMediaId());
    assertEquals(existingAvatarId, result.avatarMediaId());
  }

  @Test
  void patch_avatarOnly_whenExistingEntity_updatesAvatar() throws PersistenceException {
    var userId = UUID.randomUUID();
    var avatarId = UUID.randomUUID();

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");
    entity.setMeasurementSystem(MeasurementSystem.METRIC);
    entity.setTemperatureUnit(TemperatureUnit.CELSIUS);
    var user = new InternalUserEntity();
    user.setId(userId);
    user.setUsername("preferred-user");

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(userPreferenceRepo.save(entity)).thenReturn(entity);
    when(internalUserRepo.findById(userId)).thenReturn(Optional.of(user));

    var result = repository.patch(userId, UserPreferencePatch.avatarOnly(avatarId));

    assertEquals("preferred-user", result.username());
    assertEquals(avatarId, entity.getAvatarMediaId());
    assertEquals(avatarId, result.avatarMediaId());
  }

  @Test
  void patch_avatarOnly_clearAvatar_setsNull() throws PersistenceException {
    var userId = UUID.randomUUID();
    var oldAvatarId = UUID.randomUUID();

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");
    entity.setAvatarMediaId(oldAvatarId);
    var user = new InternalUserEntity();
    user.setId(userId);
    user.setUsername("preferred-user");

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.of(entity));
    when(userPreferenceRepo.save(entity)).thenReturn(entity);
    when(internalUserRepo.findById(userId)).thenReturn(Optional.of(user));

    var result = repository.patch(userId, UserPreferencePatch.avatarOnly(null));

    assertEquals("preferred-user", result.username());
    assertNull(entity.getAvatarMediaId());
    assertNull(result.avatarMediaId());
  }

  @Test
  void patch_avatarOnly_whenEntityMissing_createsWithDefaultLocale() throws PersistenceException {
    var userId = UUID.randomUUID();
    var avatarId = UUID.randomUUID();

    when(userPreferenceRepo.findByUserId(userId)).thenReturn(Optional.empty());
    when(internalUserRepo.findById(userId)).thenReturn(Optional.empty());
    when(userPreferenceRepo.save(any(UserPreferenceEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceEntity.class));

    var result = repository.patch(userId, UserPreferencePatch.avatarOnly(avatarId));

    verify(userPreferenceRepo).save(entityCaptor.capture());
    var saved = entityCaptor.getValue();
    assertEquals(userId, saved.getUserId());
    assertEquals("en", saved.getLocale());
    assertEquals(avatarId, saved.getAvatarMediaId());
    assertNull(result.username());
    assertEquals(avatarId, result.avatarMediaId());
  }
}
