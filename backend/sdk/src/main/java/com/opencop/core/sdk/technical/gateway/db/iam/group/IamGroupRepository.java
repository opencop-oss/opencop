// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface IamGroupRepository
    extends JpaRepository<GroupEntity, UUID>, JpaSpecificationExecutor<GroupEntity> {

  boolean existsByName(String name);

  Optional<GroupEntity> findByIdpIssuerAndIdpGroupId(String idpIssuer, String idpGroupId);

  Optional<GroupEntity> findByIdAndIdpIssuer(UUID id, String idpIssuer);

  Optional<GroupEntity> findByIdpIssuerAndName(String idpIssuer, String name);

  Optional<GroupEntity> findByIdpIssuerIsNullAndName(String name);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query(
      "select userGroup from GroupEntity userGroup"
          + " where userGroup.id in :ids order by userGroup.id")
  List<GroupEntity> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);
}
