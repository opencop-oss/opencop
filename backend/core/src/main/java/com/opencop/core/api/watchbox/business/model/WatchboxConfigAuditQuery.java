// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import java.time.Instant;
import java.util.UUID;

/** Structured, Watchbox-local query for configuration audit history. */
public record WatchboxConfigAuditQuery(
    UUID watchboxId,
    WatchboxConfigAuditEntry.Operation operation,
    UUID actorUserId,
    Instant occurredFrom,
    Instant occurredTo,
    int page,
    int size) {

  public static final int MAX_PAGE_SIZE = 100;

  public WatchboxConfigAuditQuery {
    if (page < 0) {
      throw new IllegalArgumentException("page must be non-negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    if ((long) page * size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("page offset exceeds the supported range");
    }
    if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
      throw new IllegalArgumentException("occurredFrom must not be after occurredTo");
    }
  }
}
