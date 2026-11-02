// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayerMediaJpaRepository extends JpaRepository<LayerMediaEntity, LayerMediaId> {

  boolean existsByIdLayerIdAndIdMediaId(UUID layerId, UUID mediaId);
}
