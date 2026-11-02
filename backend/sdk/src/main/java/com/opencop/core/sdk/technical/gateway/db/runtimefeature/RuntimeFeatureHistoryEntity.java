// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "runtime_feature_history", schema = "config")
public class RuntimeFeatureHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "feature_name", nullable = false, length = 64)
  private String featureName;

  @Enumerated(EnumType.STRING)
  @Column(name = "consumer_class", nullable = false, length = 32)
  private RuntimeFeatureConsumerClassEntity consumerClass;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RuntimeFeatureHistoryEntry.Operation operation;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_audience", columnDefinition = "jsonb")
  private String beforeAudience;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_audience", nullable = false, columnDefinition = "jsonb")
  private String afterAudience;

  @Column(name = "revision_before")
  private Long revisionBefore;

  @Column(name = "revision_after", nullable = false)
  private long revisionAfter;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "actor_full_name", length = 255)
  private String actorFullName;

  @Column(name = "actor_email", length = 320)
  private String actorEmail;

  @Column(name = "actor_global_admin", nullable = false)
  private boolean actorGlobalAdmin;

  protected RuntimeFeatureHistoryEntity() {}

  private RuntimeFeatureHistoryEntity(
      String featureName,
      RuntimeFeatureConsumerClassEntity consumerClass,
      RuntimeFeatureHistoryEntry.Operation operation,
      String beforeAudience,
      String afterAudience,
      Long revisionBefore,
      long revisionAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    this.featureName = featureName;
    this.consumerClass = consumerClass;
    this.operation = operation;
    this.beforeAudience = beforeAudience;
    this.afterAudience = afterAudience;
    this.revisionBefore = revisionBefore;
    this.revisionAfter = revisionAfter;
    this.occurredAt = occurredAt;
    this.actorUserId = actorUserId;
    this.actorFullName = actorFullName;
    this.actorEmail = actorEmail;
    this.actorGlobalAdmin = actorGlobalAdmin;
  }

  static RuntimeFeatureHistoryEntity create(
      String featureName,
      String afterAudience,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    return new RuntimeFeatureHistoryEntity(
        featureName,
        RuntimeFeatureConsumerClassEntity.FRONTEND_ONLY,
        RuntimeFeatureHistoryEntry.Operation.CREATE,
        null,
        afterAudience,
        null,
        0,
        occurredAt,
        actorUserId,
        actorFullName,
        actorEmail,
        actorGlobalAdmin);
  }

  static RuntimeFeatureHistoryEntity patch(
      String featureName,
      RuntimeFeatureConsumerClassEntity consumerClass,
      String beforeAudience,
      String afterAudience,
      long revisionBefore,
      long revisionAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    return new RuntimeFeatureHistoryEntity(
        featureName,
        consumerClass,
        RuntimeFeatureHistoryEntry.Operation.PATCH,
        beforeAudience,
        afterAudience,
        revisionBefore,
        revisionAfter,
        occurredAt,
        actorUserId,
        actorFullName,
        actorEmail,
        actorGlobalAdmin);
  }

  public Long getId() {
    return id;
  }

  public String getFeatureName() {
    return featureName;
  }

  RuntimeFeatureConsumerClassEntity getConsumerClass() {
    return consumerClass;
  }

  public RuntimeFeatureHistoryEntry.Operation getOperation() {
    return operation;
  }

  public String getBeforeAudience() {
    return beforeAudience;
  }

  public String getAfterAudience() {
    return afterAudience;
  }

  public Long getRevisionBefore() {
    return revisionBefore;
  }

  public long getRevisionAfter() {
    return revisionAfter;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getActorFullName() {
    return actorFullName;
  }

  public String getActorEmail() {
    return actorEmail;
  }

  public boolean isActorGlobalAdmin() {
    return actorGlobalAdmin;
  }
}
