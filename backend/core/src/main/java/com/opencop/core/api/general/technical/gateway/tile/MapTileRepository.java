// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MapTileRepository extends JpaRepository<MapTileEntity, UUID> {

  boolean existsByIsPrimaryTrue();

  boolean existsByNameAndIdNot(String name, UUID id);

  Optional<MapTileEntity> findByIsPrimaryTrue();

  List<MapTileEntity> findAllByEnabledTrue();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "CALL public.set_primary_map_tile(CAST(:id AS uuid))", nativeQuery = true)
  void setPrimaryByProcedure(@Param("id") UUID id);
}
