// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "PatchableDataSource")
@Table(name = "data_source", schema = "profiles")
@DynamicUpdate
public class PatchableDataSourceEntity {

  private enum PersistedAccessMode {
    R(AccessMode.READ),
    W(AccessMode.WRITE),
    RW(AccessMode.READ_WRITE);

    private final AccessMode domainValue;

    PersistedAccessMode(AccessMode domainValue) {
      this.domainValue = domainValue;
    }

    private static PersistedAccessMode fromDomain(AccessMode accessMode) {
      return switch (accessMode) {
        case READ -> R;
        case WRITE -> W;
        case READ_WRITE -> RW;
      };
    }
  }

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  // A metadata patch never changes the connection's mappers; only an access change does.
  @Column(name = "ingress_mapper_id", insertable = false)
  private UUID ingressMapperId;

  @Column(name = "egress_mapper_id", insertable = false)
  private UUID egressMapperId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "provider", columnDefinition = "profiles.transport_kind")
  private TransportKind transportKind;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "access", nullable = false, columnDefinition = "profiles.access_mode")
  private PersistedAccessMode access;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "default_visible", nullable = false)
  private boolean defaultVisible;

  @Column(name = "integration_id")
  private UUID integrationId;

  @Column(name = "connection_schema_version")
  private Integer connectionSchemaVersion;

  // No updated_at field: profiles.data_source carries a BEFORE UPDATE trigger (trg_ds_touch) that
  // stamps the column, so mapping it here would put a second writer on it that the trigger
  // overwrites anyway.
  @Column(name = "updated_by")
  private UUID updatedBy;

  protected PatchableDataSourceEntity() {}

  ConnectionMetadata toMetadata() {
    return new ConnectionMetadata(
        id,
        name,
        ingressMapperId,
        transportKind,
        access.domainValue,
        enabled,
        archivedAt,
        defaultVisible,
        integrationReference());
  }

  void apply(ValidatedConnectionMetadataPatch patch, UUID actorId) {
    if (patch.name() != null) {
      name = patch.name();
    }
    if (patch.defaultVisible() != null) {
      defaultVisible = patch.defaultVisible();
    }
    if (patch.access() != null) {
      access = PersistedAccessMode.fromDomain(patch.access());
    }
    if (patch.integration() != null) {
      integrationId = patch.integration().integrationId();
      connectionSchemaVersion = patch.integration().schemaVersion();
    }
    updatedBy = actorId;
  }

  void applyAccess(ConnectionAccess connectionAccess, UUID actorId) {
    access = PersistedAccessMode.fromDomain(connectionAccess.mode());
    ingressMapperId = connectionAccess.ingressMapperId().orElse(null);
    egressMapperId = connectionAccess.egressMapperId().orElse(null);
    updatedBy = actorId;
  }

  private IntegrationConnectionSchemaReference integrationReference() {
    return integrationId == null
        ? null
        : new IntegrationConnectionSchemaReference(integrationId, connectionSchemaVersion);
  }

  public AccessMode accessMode() {
    return access.domainValue;
  }
}
