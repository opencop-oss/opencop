// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.remarks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemarkJpaRepository extends JpaRepository<RemarkEntity, UUID> {

  @EntityGraph(attributePaths = "internalUser")
  List<RemarkEntity> findBySourceIdAndObjectIdOrderByCreatedAtDesc(UUID sourceId, String objectId);

  @EntityGraph(attributePaths = "internalUser")
  Optional<RemarkEntity> findById(UUID id);
}
