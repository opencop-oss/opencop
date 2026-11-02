// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(
    schema = DeadLetterAdminContextSchema.SCHEMA,
    name = DeadLetterAdminContextSchema.SOURCE_CONTEXT_TABLE)
class ProcessorDeadLetterSourceContextEntity {

  @Id
  @Column(name = DeadLetterAdminContextSchema.FAILURE_ID_COLUMN)
  private UUID failureId;

  @Column(name = DeadLetterAdminContextSchema.SOURCE_ID_COLUMN, nullable = false)
  private UUID sourceId;

  protected ProcessorDeadLetterSourceContextEntity() {}

  ProcessorDeadLetterSourceContextEntity(UUID failureId, UUID sourceId) {
    this.failureId = failureId;
    this.sourceId = sourceId;
  }

  UUID getFailureId() {
    return failureId;
  }

  UUID getSourceId() {
    return sourceId;
  }
}
