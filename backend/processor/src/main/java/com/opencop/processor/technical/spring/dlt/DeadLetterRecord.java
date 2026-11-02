// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterRecord(
    UUID id,
    State state,
    Instant stateUpdatedAt,
    Instant updatedAt,
    Stage stage,
    FailureKind failureKind,
    String originalTopic,
    Integer originalPartition,
    Long originalOffset,
    String dltTopic,
    int dltPartition,
    long dltOffset,
    String exceptionClass,
    String exceptionMessage,
    int replayCount,
    String contextJson,
    String headersJson,
    String payloadRef,
    String payloadSha256,
    Long payloadSizeBytes,
    Instant payloadExpiresAt,
    Instant indexedAt,
    AdminContext adminContext) {

  public enum State {
    OPEN,
    REPLAYED,
    ARCHIVED
  }

  public enum Stage {
    RAW,
    VALIDATION,
    REAL_TIME,
    WATCHBOX,
    PERSIST,
    NOTIFICATION,
    JOBS,
    HISTORY,
    BYOD,
    UNKNOWN
  }

  public enum FailureKind {
    CONTROL_UNSUPPORTED,
    MALFORMED,
    MAPPING_ERROR,
    INFRA,
    PROCESSING,
    UNKNOWN
  }

  public record AdminContext(UUID sourceId, UUID jobId, UUID mapperId, UUID importId) {

    static AdminContext empty() {
      return new AdminContext(null, null, null, null);
    }
  }

  public DeadLetterRecord withAdminContext(AdminContext context) {
    return new DeadLetterRecord(
        id,
        state,
        stateUpdatedAt,
        updatedAt,
        stage,
        failureKind,
        originalTopic,
        originalPartition,
        originalOffset,
        dltTopic,
        dltPartition,
        dltOffset,
        exceptionClass,
        exceptionMessage,
        replayCount,
        contextJson,
        headersJson,
        payloadRef,
        payloadSha256,
        payloadSizeBytes,
        payloadExpiresAt,
        indexedAt,
        context == null ? AdminContext.empty() : context);
  }
}
