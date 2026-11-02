// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "connection_integration_settings", schema = "profiles")
public class ConnectionIntegrationSettingsEntity {

  @Id
  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "integration_id", nullable = false)
  private UUID integrationId;

  @Column(name = "connection_schema_version", nullable = false)
  private int connectionSchemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Map<String, Object>> settings;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private Instant updatedAt;

  protected ConnectionIntegrationSettingsEntity() {}

  ConnectionIntegrationSettingsEntity(
      UUID sourceId,
      UUID integrationId,
      int connectionSchemaVersion,
      Map<String, Map<String, Object>> settings) {
    this.sourceId = sourceId;
    this.integrationId = integrationId;
    this.connectionSchemaVersion = connectionSchemaVersion;
    this.settings = settings;
  }

  UUID sourceId() {
    return sourceId;
  }

  Map<String, Map<String, Object>> settings() {
    return settings;
  }
}
