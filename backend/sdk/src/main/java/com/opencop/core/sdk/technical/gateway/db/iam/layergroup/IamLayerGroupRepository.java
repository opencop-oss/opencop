// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamLayerGroupRepository extends JpaRepository<LayerGroupEntity, UUID> {

  boolean existsByName(String name);

  List<LayerGroupEntity> findAllByAccessScope(LayerGroupAccessScope scope);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from LayerGroupEntity g where g.id = :id")
  Optional<LayerGroupEntity> findByIdForUpdate(@Param("id") UUID id);
}
