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
    name = DeadLetterAdminContextSchema.MAPPER_CONTEXT_TABLE)
class ProcessorDeadLetterMapperContextEntity {

  @Id
  @Column(name = DeadLetterAdminContextSchema.FAILURE_ID_COLUMN)
  private UUID failureId;

  @Column(name = DeadLetterAdminContextSchema.MAPPER_ID_COLUMN, nullable = false)
  private UUID mapperId;

  protected ProcessorDeadLetterMapperContextEntity() {}

  ProcessorDeadLetterMapperContextEntity(UUID failureId, UUID mapperId) {
    this.failureId = failureId;
    this.mapperId = mapperId;
  }

  UUID getFailureId() {
    return failureId;
  }

  UUID getMapperId() {
    return mapperId;
  }
}
