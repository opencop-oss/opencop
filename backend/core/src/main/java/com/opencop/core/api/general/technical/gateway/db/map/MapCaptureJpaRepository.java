// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.map;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapCaptureJpaRepository extends JpaRepository<MapCaptureEntity, UUID> {

  List<MapCaptureEntity> findByUserId(UUID userId);
}
