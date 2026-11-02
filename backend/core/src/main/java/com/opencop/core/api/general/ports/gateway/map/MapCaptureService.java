// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.map;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.business.model.map.MapCapturePatch;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;

public interface MapCaptureService {

  MapCapture findById(UUID id) throws PersistenceException;

  List<MapCapture> findAll(UUID userId) throws PersistenceException;

  MapCapture create(UUID userId, MapCapture capture) throws PersistenceException;

  MapCapture patch(UUID id, MapCapturePatch patch, UUID userId) throws PersistenceException;

  void delete(UUID id, UUID userId) throws PersistenceException;
}
