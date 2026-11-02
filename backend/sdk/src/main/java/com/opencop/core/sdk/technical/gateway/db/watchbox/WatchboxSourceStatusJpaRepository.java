// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchboxSourceStatusJpaRepository
    extends JpaRepository<WatchboxSourceStatusEntity, WatchboxSourceStatusIdEntity> {

  List<WatchboxSourceStatusEntity> findAllByIdWatchboxIdAndIdConfigEpoch(
      UUID watchboxId, long configEpoch);

  List<WatchboxSourceStatusEntity> findAllByIdWatchboxIdInAndIdConfigEpoch(
      Collection<UUID> watchboxIds, long configEpoch);

  List<WatchboxSourceStatusEntity> findAllByIdWatchboxIdIn(Collection<UUID> watchboxIds);
}
