// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.api.general.ports.gateway.map.MapCaptureService;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MapCaptureRepository implements MapCaptureService {

  private final MapCaptureJpaRepository repo;

  public MapCaptureRepository(MapCaptureJpaRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(readOnly = true)
  public MapCapture findById(UUID id) throws PersistenceException {
    return this.repo
        .findById(id)
        .map(MapCaptureMapper::toModel)
        .orElseThrow(() -> new PersistenceException("Not Found"));
  }

  @Override
  @Transactional(readOnly = true)
  public List<MapCapture> findAll(UUID userId) throws PersistenceException {
    return this.repo.findByUserId(userId).stream().map(MapCaptureMapper::toModel).toList();
  }

  @Override
  @Transactional
  public MapCapture create(UUID userId, MapCapture capture) throws PersistenceException {
    validate(capture);
    var entity = new MapCaptureEntity();
    MapCaptureMapper.applyCreate(capture, entity, userId);
    return MapCaptureMapper.toModel(this.repo.save(entity));
  }

  @Override
  @Transactional
  public MapCapture patch(UUID captureId, MapCapturePatch patch, UUID userId)
      throws PersistenceException {
    var entity =
        this.repo
            .findById(captureId)
            .orElseThrow(
                () ->
                    new MapCapturePersistenceException.NotFound(
                        "Could not find map capture: " + captureId));

    if (!entity.getUserId().equals(userId)) {
      throw new PersistenceException("Tried patching a capture that the user does not own");
    }

    patch.name().ifPresent(entity::setName);
    patch.mapView().ifPresent(mapView -> MapCaptureMapper.applyMapView(mapView, entity));

    return MapCaptureMapper.toModel(this.repo.save(entity));
  }

  @Override
  @Transactional
  public void delete(UUID captureId, UUID userId) throws PersistenceException {
    var capture = this.repo.findById(captureId);
    if (capture.isEmpty()) {
      throw new MapCapturePersistenceException.NotFound("Could not find map capture: " + captureId);
    }
    if (!capture.get().getUserId().equals(userId)) {
      throw new PersistenceException("Tried deleting a capture that the user does not own");
    }
    this.repo.deleteById(captureId);
  }

  private void validate(MapCapture capture) throws PersistenceException {
    if (capture == null
        || capture.name() == null
        || capture.name().isBlank()
        || capture.mapView() == null) {
      throw new MapCapturePersistenceException.BadRequest(
          "Map capture name and mapView are required");
    }
  }
}
