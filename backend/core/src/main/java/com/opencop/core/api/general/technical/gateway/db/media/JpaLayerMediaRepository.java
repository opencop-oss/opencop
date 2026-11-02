// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import com.opencop.core.api.general.ports.gateway.media.LayerMediaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaLayerMediaRepository implements LayerMediaRepository {

  private final LayerMediaJpaRepository jpa;

  public JpaLayerMediaRepository(LayerMediaJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID layerId, UUID mediaId) {
    return jpa.existsByIdLayerIdAndIdMediaId(layerId, mediaId);
  }

  @Override
  @Transactional
  public void save(UUID layerId, UUID mediaId) {
    jpa.save(new LayerMediaEntity(new LayerMediaId(layerId, mediaId)));
  }
}
