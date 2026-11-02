// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamDataSourceGroupRepository extends JpaRepository<DataSourceGroupEntity, UUID> {

  boolean existsByName(String name);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from DataSourceGroupEntity g where g.id = :id")
  Optional<DataSourceGroupEntity> findByIdForUpdate(@Param("id") UUID id);
}
