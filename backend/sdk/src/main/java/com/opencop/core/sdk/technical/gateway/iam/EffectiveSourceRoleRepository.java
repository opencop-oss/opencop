// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface EffectiveSourceRoleRepository extends Repository<DataSourceGroupEntity, UUID> {

  @Query(
      value =
          """
          SELECT source_id AS "sourceId",
                 role::text AS role,
                 via
          FROM iam.effective_source_roles(:userId, :sourceId, false)
          """,
      nativeQuery = true)
  Optional<EffectiveSourceRoleRow> findActiveEffectiveRole(
      @Param("userId") UUID userId, @Param("sourceId") UUID sourceId);

  @Query(
      value =
          """
          SELECT source_id AS "sourceId",
                 role::text AS role,
                 via
          FROM iam.effective_source_roles(:userId, NULL, true)
          """,
      nativeQuery = true)
  List<EffectiveSourceRoleRow> findEffectiveRolesIncludingArchivedSources(
      @Param("userId") UUID userId);

  interface EffectiveSourceRoleRow {
    UUID getSourceId();

    String getRole();

    String getVia();
  }
}
