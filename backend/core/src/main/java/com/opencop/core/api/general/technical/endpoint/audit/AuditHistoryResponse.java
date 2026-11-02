// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.audit;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One history row on the wire.
 *
 * <p>The row id is exposed so a client can anchor on an exact row; {@code change} is passed through
 * as raw JSON rather than re-encoded as a string.
 */
public record AuditHistoryResponse(
    long id,
    AuditEntry.EntityType entityType,
    UUID entityId,
    String entityName,
    AuditEntry.Operation operation,
    Instant occurredAt,
    Caller actor,
    @JsonRawValue @Schema(type = "object") String change,
    int changeVersion) {

  public static AuditHistoryResponse from(AuditEntry entry) {
    return new AuditHistoryResponse(
        entry.id(),
        entry.entityType(),
        entry.entityId(),
        entry.entityName(),
        entry.operation(),
        entry.occurredAt(),
        entry.actor(),
        entry.change(),
        entry.changeVersion());
  }
}
