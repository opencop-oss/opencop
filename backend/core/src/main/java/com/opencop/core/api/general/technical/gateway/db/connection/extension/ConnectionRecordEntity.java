// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "connection_integration_records", schema = "profiles")
@IdClass(ConnectionRecordEntityId.class)
public class ConnectionRecordEntity {

  @Id
  @Column(name = "source_id")
  private UUID sourceId;

  @Id
  @Column(name = "extension_id")
  private String extensionId;

  @Id
  @Column(name = "collection")
  private String collection;

  @Id
  @Column(name = "record_key")
  private String recordKey;

  @Column(name = "integration_id", nullable = false, updatable = false)
  private UUID integrationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> document;

  @Column(nullable = false)
  private long revision;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  protected ConnectionRecordEntity() {}

  ConnectionRecordEntity(ConnectionRecordEntityId id, UUID integrationId, UUID createdBy) {
    this.sourceId = id.sourceId();
    this.extensionId = id.extensionId();
    this.collection = id.collection();
    this.recordKey = id.recordKey();
    this.integrationId = integrationId;
    this.createdBy = createdBy;
  }

  void write(Map<String, Object> document, long revision, UUID updatedBy) {
    this.document = document;
    this.revision = revision;
    this.updatedBy = updatedBy;
  }

  /** Whether the row was written for the integration. */
  boolean belongsTo(UUID integrationId) {
    return this.integrationId.equals(integrationId);
  }

  ConnectionRecord toRecord() {
    return new ConnectionRecord(
        collection, recordKey, document, revision, createdAt, createdBy, updatedAt, updatedBy);
  }
}
