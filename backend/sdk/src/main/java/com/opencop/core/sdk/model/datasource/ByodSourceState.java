// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import java.time.Instant;
import java.util.UUID;

/**
 * Desired state of a BYOD source, published to the compacted topic {@code opencop.byod.sources}.
 * Each Processor instance consumes this to converge its local listener containers.
 */
public record ByodSourceState(
    UUID sourceId,
    String topicName,
    String username,
    Status status,
    Instant updatedAt,
    int version) {

  /** Current schema version. Bump when the record shape changes. */
  public static final int CURRENT_VERSION = 1;

  public enum Status {
    STARTED,
    STOPPED,
    DELETED
  }
}
