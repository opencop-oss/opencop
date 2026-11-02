// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

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

@Entity
@Table(name = "watchbox_config_audit", schema = "watchbox")
public class WatchboxConfigAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "watchbox_id", nullable = false)
  private UUID watchboxId;

  @Column(nullable = false, length = 16)
  private String operation;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_snapshot", columnDefinition = "jsonb")
  private String beforeSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_snapshot", columnDefinition = "jsonb")
  private String afterSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reset_reasons", nullable = false, columnDefinition = "jsonb")
  private String resetReasons;

  @Column(name = "config_epoch_before")
  private Long configEpochBefore;

  @Column(name = "config_epoch_after")
  private Long configEpochAfter;

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

  protected WatchboxConfigAuditEntity() {}

  private WatchboxConfigAuditEntity(
      UUID watchboxId,
      String operation,
      String beforeSnapshot,
      String afterSnapshot,
      String resetReasons,
      Long configEpochBefore,
      Long configEpochAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    this.watchboxId = watchboxId;
    this.operation = operation;
    this.beforeSnapshot = beforeSnapshot;
    this.afterSnapshot = afterSnapshot;
    this.resetReasons = resetReasons;
    this.configEpochBefore = configEpochBefore;
    this.configEpochAfter = configEpochAfter;
    this.occurredAt = occurredAt;
    this.actorUserId = actorUserId;
    this.actorFullName = actorFullName;
    this.actorEmail = actorEmail;
    this.actorGlobalAdmin = actorGlobalAdmin;
  }

  public static WatchboxConfigAuditEntity create(
      UUID watchboxId,
      String afterSnapshot,
      long configEpochAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    return new WatchboxConfigAuditEntity(
        watchboxId,
        "CREATE",
        null,
        afterSnapshot,
        "[]",
        null,
        configEpochAfter,
        occurredAt,
        actorUserId,
        actorFullName,
        actorEmail,
        actorGlobalAdmin);
  }

  public static WatchboxConfigAuditEntity patch(
      UUID watchboxId,
      String beforeSnapshot,
      String afterSnapshot,
      String resetReasons,
      long configEpochBefore,
      long configEpochAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    return new WatchboxConfigAuditEntity(
        watchboxId,
        "PATCH",
        beforeSnapshot,
        afterSnapshot,
        resetReasons,
        configEpochBefore,
        configEpochAfter,
        occurredAt,
        actorUserId,
        actorFullName,
        actorEmail,
        actorGlobalAdmin);
  }

  public static WatchboxConfigAuditEntity archive(
      UUID watchboxId,
      String beforeSnapshot,
      long configEpochBefore,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {
    return new WatchboxConfigAuditEntity(
        watchboxId,
        "ARCHIVE",
        beforeSnapshot,
        null,
        "[]",
        configEpochBefore,
        null,
        occurredAt,
        actorUserId,
        actorFullName,
        actorEmail,
        actorGlobalAdmin);
  }

  public Long getId() {
    return id;
  }

  public UUID getWatchboxId() {
    return watchboxId;
  }

  public String getOperation() {
    return operation;
  }

  public String getBeforeSnapshot() {
    return beforeSnapshot;
  }

  public String getAfterSnapshot() {
    return afterSnapshot;
  }

  public String getResetReasons() {
    return resetReasons;
  }

  public Long getConfigEpochBefore() {
    return configEpochBefore;
  }

  public Long getConfigEpochAfter() {
    return configEpochAfter;
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
