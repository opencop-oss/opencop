// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only view of the integration settings overrides Core stores for a source, joined to the
 * source it belongs to.
 */
@Entity
@Immutable
@Table(name = "connection_integration_settings", schema = "profiles")
public class DesiredIntegrationSettingsEntity {

  @Id
  @Column(name = "source_id")
  private UUID sourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Map<String, Object>> settings;

  protected DesiredIntegrationSettingsEntity() {}

  Map<String, Map<String, Object>> getSettings() {
    return settings;
  }
}
