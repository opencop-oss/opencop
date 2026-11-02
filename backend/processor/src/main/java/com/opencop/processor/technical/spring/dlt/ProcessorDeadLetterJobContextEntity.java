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
    name = DeadLetterAdminContextSchema.JOB_CONTEXT_TABLE)
class ProcessorDeadLetterJobContextEntity {

  @Id
  @Column(name = DeadLetterAdminContextSchema.FAILURE_ID_COLUMN)
  private UUID failureId;

  @Column(name = DeadLetterAdminContextSchema.JOB_ID_COLUMN, nullable = false)
  private UUID jobId;

  protected ProcessorDeadLetterJobContextEntity() {}

  ProcessorDeadLetterJobContextEntity(UUID failureId, UUID jobId) {
    this.failureId = failureId;
    this.jobId = jobId;
  }

  UUID getFailureId() {
    return failureId;
  }

  UUID getJobId() {
    return jobId;
  }
}
