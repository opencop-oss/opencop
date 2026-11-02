// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.notification;

import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "opencop_notification")
public class NotificationEntity {

  @Id private UUID id;

  @Column(name = "correlation_id")
  private UUID correlationId;

  @Column(name = "issuer_user_id", nullable = false)
  private UUID issuerUserId;

  @Column(name = "target_user_id", nullable = false)
  private UUID targetUserId;

  @Column(nullable = false)
  private String type;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, columnDefinition = "notification_severity")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private NotificationSeverityType severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "notification_status")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private NotificationStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "read_at", updatable = false)
  private Instant readAt;

  public NotificationEntity() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(UUID correlationId) {
    this.correlationId = correlationId;
  }

  public UUID getIssuerUserId() {
    return issuerUserId;
  }

  public void setIssuerUserId(UUID issuerUserId) {
    this.issuerUserId = issuerUserId;
  }

  public UUID getTargetUserId() {
    return targetUserId;
  }

  public void setTargetUserId(UUID targetUserId) {
    this.targetUserId = targetUserId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public NotificationSeverityType getSeverity() {
    return severity;
  }

  public void setSeverity(NotificationSeverityType severity) {
    this.severity = severity;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public boolean isRead() {
    return readAt != null;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void setReadAt(Instant readAt) {
    this.readAt = readAt;
  }
}
