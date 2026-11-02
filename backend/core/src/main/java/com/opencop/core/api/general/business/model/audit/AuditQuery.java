// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import java.time.Instant;
import java.util.UUID;

/**
 * A bounded read of the ledger. Every non-null filter is ANDed; both time bounds are inclusive, so
 * adjacent closed intervals may each contain their shared boundary.
 *
 * <p>Ordering is fixed newest-first and is not a caller's to choose. Paging is by offset, which is
 * deterministic for unchanged data but is not a snapshot: rows written between two page requests
 * shift later offsets, so a client reading several pages must expect drift rather than assume
 * consistency.
 */
public record AuditQuery(
    EntityType entityType,
    UUID entityId,
    Operation operation,
    UUID actorUserId,
    Instant occurredFrom,
    Instant occurredTo,
    int page,
    int size) {

  public static final int MAXIMUM_SIZE = 100;
  public static final int DEFAULT_SIZE = 25;
}
