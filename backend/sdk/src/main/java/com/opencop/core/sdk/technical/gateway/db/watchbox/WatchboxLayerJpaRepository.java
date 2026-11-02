// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WatchboxLayerJpaRepository
    extends JpaRepository<WatchboxLayerEntity, WatchboxLayerIdEntity> {

  List<WatchboxLayerEntity> findAllByIdWatchboxId(UUID watchboxId);

  List<WatchboxLayerEntity> findAllByIdWatchboxIdIn(Collection<UUID> watchboxIds);

  List<WatchboxLayerEntity> findAllByIdLayerIdIn(Collection<UUID> layerIds);

  @Modifying
  @Query("delete from WatchboxLayerEntity l where l.id.watchboxId = :watchboxId")
  void deleteAllByWatchboxId(UUID watchboxId);
}
