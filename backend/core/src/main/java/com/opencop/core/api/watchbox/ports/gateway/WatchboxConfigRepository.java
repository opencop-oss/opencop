// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxSourceStatusRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WatchboxConfigRepository {

  WatchboxConfig create(WatchboxConfig config, UUID createdBy);

  WatchboxConfig update(WatchboxConfig config);

  void archive(UUID watchboxId, UUID archivedBy);

  Optional<WatchboxConfig> findActive(UUID watchboxId);

  /**
   * Like {@link #findActive}, but locks the config row until the caller's transaction ends, so an
   * epoch-allocating PATCH cannot race another mutation onto the same tracking generation and an
   * ARCHIVE before-snapshot describes the configuration version actually archived.
   */
  Optional<WatchboxConfig> findActiveForUpdate(UUID watchboxId);

  List<WatchboxConfig> listActive();

  List<WatchboxSourceStatusRecord> findSourceStatuses(UUID watchboxId, long configEpoch);

  /**
   * Bulk form of {@link #findSourceStatuses} for one list request: each watchbox's statuses at that
   * watchbox's own config epoch, resolved in one read. Watchboxes without status rows are absent
   * from the result.
   */
  Map<UUID, List<WatchboxSourceStatusRecord>> findSourceStatusesForWatchboxes(
      Map<UUID, Long> configEpochByWatchboxId);
}
