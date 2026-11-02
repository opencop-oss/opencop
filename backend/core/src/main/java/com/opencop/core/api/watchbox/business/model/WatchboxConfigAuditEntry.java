// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WatchboxConfigAuditEntry(
    long id,
    UUID watchboxId,
    Operation operation,
    WatchboxConfigAuditSnapshot beforeSnapshot,
    WatchboxConfigAuditSnapshot afterSnapshot,
    List<WatchboxResetReason> resetReasons,
    Long configEpochBefore,
    Long configEpochAfter,
    Instant occurredAt,
    Actor actor) {

  public WatchboxConfigAuditEntry {
    Objects.requireNonNull(watchboxId, "watchboxId");
    Objects.requireNonNull(operation, "operation");
    resetReasons = List.copyOf(Objects.requireNonNull(resetReasons, "resetReasons"));
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(actor, "actor");
  }

  public enum Operation {
    /** A Watchbox configuration was created. */
    CREATE,

    /** An accepted Watchbox configuration patch was applied. */
    PATCH,

    /** A Watchbox configuration was soft-archived. */
    ARCHIVE
  }

  public record Actor(UUID userId, String fullName, String email, boolean globalAdmin) {

    public Actor {
      Objects.requireNonNull(userId, "userId");
    }
  }
}
