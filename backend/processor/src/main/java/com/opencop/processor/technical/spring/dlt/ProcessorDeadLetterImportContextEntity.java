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
    name = DeadLetterAdminContextSchema.IMPORT_CONTEXT_TABLE)
class ProcessorDeadLetterImportContextEntity {

  @Id
  @Column(name = DeadLetterAdminContextSchema.FAILURE_ID_COLUMN)
  private UUID failureId;

  @Column(name = DeadLetterAdminContextSchema.IMPORT_ID_COLUMN, nullable = false)
  private UUID importId;

  protected ProcessorDeadLetterImportContextEntity() {}

  ProcessorDeadLetterImportContextEntity(UUID failureId, UUID importId) {
    this.failureId = failureId;
    this.importId = importId;
  }

  UUID getFailureId() {
    return failureId;
  }

  UUID getImportId() {
    return importId;
  }
}
