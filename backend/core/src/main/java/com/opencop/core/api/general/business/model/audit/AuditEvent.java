// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.audit;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import java.util.UUID;

/**
 * One action to record, as a caller hands it to {@code AuditRecorder}.
 *
 * <p>Differs from {@link AuditEntry} in carrying no id and no occurrence time: both are the store's
 * to assign, so a caller cannot backdate a row.
 *
 * @param entityName the resource's name at this moment; truncated to {@link #ENTITY_NAME_MAXIMUM}
 *     by the store, which records {@code nameTruncated} in the change when it does
 * @param change the allowlisted projection of what moved — never a request body, and never the
 *     value of a secret, credential, token or key. The store decides how it is written
 */
public record AuditEvent(
    EntityType entityType,
    UUID entityId,
    String entityName,
    Operation operation,
    Caller actor,
    AuditChange change,
    int changeVersion) {

  /** Matches the column. Nothing caps layer, layer group or mapper names at the source today. */
  public static final int ENTITY_NAME_MAXIMUM = 255;

  /** The current projection shape. Bump only alongside a change the old shape cannot express. */
  public static final int CURRENT_CHANGE_VERSION = 1;
}
