// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditEntry;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditSnapshot;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatchboxConfigAuditResponse(
    long id,
    UUID watchboxId,
    WatchboxConfigAuditEntry.Operation operation,
    WatchboxConfigAuditSnapshot beforeSnapshot,
    WatchboxConfigAuditSnapshot afterSnapshot,
    List<WatchboxResetReason> resetReasons,
    Long configEpochBefore,
    Long configEpochAfter,
    Instant occurredAt,
    WatchboxConfigAuditActorResponse actor) {

  static WatchboxConfigAuditResponse from(WatchboxConfigAuditEntry entry) {
    return new WatchboxConfigAuditResponse(
        entry.id(),
        entry.watchboxId(),
        entry.operation(),
        entry.beforeSnapshot(),
        entry.afterSnapshot(),
        entry.resetReasons(),
        entry.configEpochBefore(),
        entry.configEpochAfter(),
        entry.occurredAt(),
        new WatchboxConfigAuditActorResponse(
            entry.actor().userId(),
            entry.actor().fullName(),
            entry.actor().email(),
            entry.actor().globalAdmin()));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WatchboxConfigAuditActorResponse(
      UUID userId, String fullName, String email, boolean globalAdmin) {}
}
