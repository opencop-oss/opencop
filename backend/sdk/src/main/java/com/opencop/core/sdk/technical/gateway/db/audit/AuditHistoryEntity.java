// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A row of {@code audit.audit_history}. Append-only: nothing updates or deletes one. */
@Entity
@Table(name = "audit_history", schema = "audit")
public class AuditHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "entity_type", nullable = false, length = 32)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "entity_name", length = 255)
  private String entityName;

  @Column(nullable = false, length = 32)
  private String operation;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String change;

  @Column(name = "change_version", nullable = false)
  private int changeVersion;

  protected AuditHistoryEntity() {}

  public AuditHistoryEntity(
      String entityType,
      UUID entityId,
      String entityName,
      String operation,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin,
      String change,
      int changeVersion) {
    this.entityType = entityType;
    this.entityId = entityId;
    this.entityName = entityName;
    this.operation = operation;
    this.occurredAt = occurredAt;
    this.actorUserId = actorUserId;
    this.actorFullName = actorFullName;
    this.actorEmail = actorEmail;
    this.actorGlobalAdmin = actorGlobalAdmin;
    this.change = change;
    this.changeVersion = changeVersion;
  }

  public Long getId() {
    return id;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getEntityName() {
    return entityName;
  }

  public String getOperation() {
    return operation;
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

  public String getChange() {
    return change;
  }

  public int getChangeVersion() {
    return changeVersion;
  }
}
