// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.repository;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "data_source", schema = "profiles")
public class DesiredSocketSourceEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  private String tenant;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(columnDefinition = "profiles.transport_kind")
  private TransportKind provider;

  // Null for a source that only writes: it decodes nothing it receives.
  @Column(name = "ingress_mapper_id")
  private UUID ingressMapperId;

  // Null for a source that only reads.
  @Column(name = "egress_mapper_id")
  private UUID egressMapperId;

  @Column(name = "integration_id")
  private UUID integrationId;

  @Column(name = "connection_schema_version")
  private Integer connectionSchemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "connection_spec", nullable = false, columnDefinition = "jsonb")
  private String connectionSpec;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false, columnDefinition = "profiles.access_mode")
  private String access;

  @Column(name = "assigned_instance")
  private String assignedInstance;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "default_visible", nullable = false)
  private boolean defaultVisible;

  // Absent when the source overrides no integration setting; it shares the source's key.
  @OneToOne @PrimaryKeyJoinColumn private DesiredIntegrationSettingsEntity integrationSettings;

  protected DesiredSocketSourceEntity() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTenant() {
    return tenant;
  }

  public TransportKind getProvider() {
    return provider;
  }

  public UUID getIngressMapperId() {
    return ingressMapperId;
  }

  public UUID getEgressMapperId() {
    return egressMapperId;
  }

  public UUID getIntegrationId() {
    return integrationId;
  }

  public Integer getConnectionSchemaVersion() {
    return connectionSchemaVersion;
  }

  public String getConnectionSpec() {
    return connectionSpec;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getAccess() {
    return access;
  }

  public String getAssignedInstance() {
    return assignedInstance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  /** Returns the stored integration settings overrides, or null when the source has none. */
  public Map<String, Map<String, Object>> getIntegrationSettings() {
    return integrationSettings == null ? null : integrationSettings.getSettings();
  }

  public boolean isDefaultVisible() {
    return defaultVisible;
  }

  public boolean isActive() {
    return archivedAt == null;
  }
}
