// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureMapper;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaUserPreferenceRepository implements UserPreferenceRepository {

  private final UserPreferenceJpaRepository userPreferenceRepo;
  private final MapCaptureJpaRepository mapCaptureRepo;
  private final MapTileService mapTileService;
  private final InternalUserRepository internalUserRepo;

  public JpaUserPreferenceRepository(
      UserPreferenceJpaRepository userPreferenceRepo,
      MapCaptureJpaRepository mapCaptureRepo,
      MapTileService mapTileService,
      InternalUserRepository internalUserRepo) {
    this.userPreferenceRepo = userPreferenceRepo;
    this.mapCaptureRepo = mapCaptureRepo;
    this.mapTileService = mapTileService;
    this.internalUserRepo = internalUserRepo;
  }

  @Override
  @Transactional(readOnly = true)
  public UserPreference findByUserId(UUID userId) {
    return this.userPreferenceRepo
        .findByUserId(userId)
        .map(entity -> toModel(entity, userId))
        .orElse(toModel(defaultEntity(), userId));
  }

  @Override
  @Transactional
  public UserPreference create(UUID userId) {

    var entity = new UserPreferenceEntity();
    entity.setUserId(userId);
    entity.setLocale("en");
    entity.setMeasurementSystem(MeasurementSystem.METRIC);
    entity.setTemperatureUnit(TemperatureUnit.FAHRENHEIT);
    entity.setTimezone("UTC");
    entity.setZoomBehavior(ZoomBehavior.CENTER);
    entity.setAvatarMediaId(null);
    entity.setMapTileId(null);
    entity.setMapCaptureId(null);

    var saved = this.userPreferenceRepo.save(entity);
    return toModel(saved, userId);
  }

  @Override
  @Transactional
  public UserPreference patch(UUID userId, UserPreferencePatch patch) throws PersistenceException {
    var entityOpt = this.userPreferenceRepo.findByUserId(userId);

    UserPreferenceEntity entity;
    if (entityOpt.isPresent()) {
      entity = entityOpt.get();
    } else {
      entity = new UserPreferenceEntity();
      entity.setUserId(userId);
      entity.setLocale("en");
    }

    if (patch.username().isPresent()) {
      var username = patch.username().get();
      var existingUser = internalUserRepo.findByUsername(username);
      if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
        throw new GlobalOpenCopException(
            "Username already taken: " + username, HttpStatus.CONFLICT);
      }

      var user =
          internalUserRepo
              .findById(userId)
              .orElseThrow(
                  () ->
                      new PersistenceException(
                          "User not found while updating preferences: " + userId));
      user.setUsername(username);
      internalUserRepo.save(user);
    }
    patch.locale().ifPresent(entity::setLocale);
    patch.measurementSystem().ifPresent(entity::setMeasurementSystem);
    patch.temperatureUnit().ifPresent(entity::setTemperatureUnit);
    patch.timezone().ifPresent(entity::setTimezone);
    patch.zoomBehavior().ifPresent(entity::setZoomBehavior);
    patch.defaultTileId().ifPresent(entity::setMapTileId);
    patch.initialView().ifPresent(entity::setMapCaptureId);
    if (patch.avatarMediaId() != null) {
      entity.setAvatarMediaId(patch.avatarMediaId().orElse(null));
    }

    var saved = this.userPreferenceRepo.save(entity);
    return toModel(saved, userId);
  }

  private UserPreference toModel(UserPreferenceEntity entity, UUID userId) {
    MapCapture initialView = null;
    if (entity.getMapCaptureId() != null) {
      initialView =
          this.mapCaptureRepo
              .findById(entity.getMapCaptureId())
              .map(MapCaptureMapper::toModel)
              .orElse(null);
    }

    MapTile initialMapTile = null;
    if (entity.getMapTileId() != null) {
      var initialMapTileOpt = this.mapTileService.getOneSanitized(entity.getMapTileId());
      if (initialMapTileOpt.isPresent()) {
        initialMapTile = initialMapTileOpt.get();
      }
    }

    return new UserPreference(
        findUsername(userId),
        entity.getLocale(),
        entity.getMeasurementSystem(),
        entity.getTemperatureUnit(),
        entity.getTimezone(),
        entity.getZoomBehavior(),
        entity.getAvatarMediaId(),
        null, // avatar MediaView — enriched at the endpoint layer
        initialView,
        initialMapTile);
  }

  private String findUsername(UUID userId) {
    return internalUserRepo.findById(userId).map(user -> user.getUsername()).orElse(null);
  }

  private static UserPreferenceEntity defaultEntity() {
    var entity = new UserPreferenceEntity();
    entity.setUserId(UUID.randomUUID());
    entity.setLocale("en");
    entity.setMeasurementSystem(MeasurementSystem.METRIC);
    entity.setTemperatureUnit(TemperatureUnit.CELSIUS);
    entity.setTimezone("UTC");
    entity.setZoomBehavior(ZoomBehavior.CENTER);
    return entity;
  }
}
