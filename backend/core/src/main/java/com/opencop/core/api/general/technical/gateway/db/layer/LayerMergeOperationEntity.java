// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layer;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "layer_merge_operation", schema = "processor")
public class LayerMergeOperationEntity {

  @Id
  @Column(name = "target_layer_id", nullable = false, updatable = false)
  private UUID targetLayerId;

  @Column(name = "job_id", nullable = false, updatable = false)
  private UUID jobId;

  @Column(name = "source_layer_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] sourceLayerIds;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "phase",
      nullable = false,
      columnDefinition = "processor.layer_merge_operation_phase")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerMergeOperationPhase phase;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "failure_reason")
  private String failureReason;

  public UUID getTargetLayerId() {
    return targetLayerId;
  }

  public void setTargetLayerId(UUID targetLayerId) {
    this.targetLayerId = targetLayerId;
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID[] getSourceLayerIds() {
    return sourceLayerIds;
  }

  public void setSourceLayerIds(UUID[] sourceLayerIds) {
    this.sourceLayerIds = sourceLayerIds;
  }

  public LayerMergeOperationPhase getPhase() {
    return phase;
  }

  public void setPhase(LayerMergeOperationPhase phase) {
    this.phase = phase;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }
}
