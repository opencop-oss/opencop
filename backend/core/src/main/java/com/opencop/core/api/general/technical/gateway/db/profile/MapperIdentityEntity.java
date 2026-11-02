// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One mapper's identity: its direction and the integration that owns it, if any. */
@Entity(name = "MapperIdentity")
@Table(name = "mapper", schema = "profiles")
public class MapperIdentityEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "qualifier", nullable = false, columnDefinition = "profiles.mapper_qualifier")
  private MapperQualifier qualifier;

  @Column(name = "integration_id")
  private UUID integrationId;

  protected MapperIdentityEntity() {}

  MapperQualifier qualifier() {
    return qualifier;
  }

  UUID integrationId() {
    return integrationId;
  }
}
