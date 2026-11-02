// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import com.opencop.core.api.watchbox.business.model.WatchboxDetection;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionPageRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WatchboxDetectionReader {
  /**
   * Caller-scoped visible ids; an empty set returns an empty page without querying. Order is fixed:
   * created_at DESC, id DESC. Page and count use two READ COMMITTED statements and may see
   * different snapshots.
   */
  PageResponse<WatchboxDetection> findPage(
      Set<UUID> watchboxIds, Set<WatchboxTransitionKind> kinds, WatchboxDetectionPageRequest page);

  Optional<WatchboxDetection> findById(UUID detectionId);
}
