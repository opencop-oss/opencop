// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationIdentityJpaRepository
    extends JpaRepository<IntegrationIdentityEntity, UUID> {

  /** The integration offering one connection schema version. */
  String SCHEMA_OWNER =
      """
      SELECT integration
      FROM IntegrationIdentityEntity integration
      WHERE integration.id = :integrationId
        AND EXISTS (
          SELECT schema
          FROM IntegrationConnectionSchemaEntity schema
          WHERE schema.id.integrationId = integration.id
            AND schema.id.schemaVersion = :schemaVersion)
      """;

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT integration FROM IntegrationIdentityEntity integration WHERE integration.id = :id")
  Optional<IntegrationIdentityEntity> findForUpdate(@Param("id") UUID id);

  @Query(SCHEMA_OWNER)
  Optional<IntegrationIdentityEntity> findSchemaOwner(
      @Param("integrationId") UUID integrationId, @Param("schemaVersion") int schemaVersion);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(SCHEMA_OWNER)
  Optional<IntegrationIdentityEntity> findSchemaOwnerForUpdate(
      @Param("integrationId") UUID integrationId, @Param("schemaVersion") int schemaVersion);
}
