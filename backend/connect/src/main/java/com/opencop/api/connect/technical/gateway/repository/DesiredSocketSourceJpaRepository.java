// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.repository;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface DesiredSocketSourceJpaRepository
    extends Repository<DesiredSocketSourceEntity, UUID> {

  /** Loads each source's settings overrides in the same query, as one desired-state row. */
  String INTEGRATION_SETTINGS = "integrationSettings";

  @EntityGraph(attributePaths = INTEGRATION_SETTINGS)
  Optional<DesiredSocketSourceEntity> findByIdAndProviderInAndArchivedAtIsNull(
      UUID id, Collection<TransportKind> providers);

  @EntityGraph(attributePaths = INTEGRATION_SETTINGS)
  List<DesiredSocketSourceEntity>
      findByEnabledTrueAndAssignedInstanceAndProviderInAndArchivedAtIsNullOrderByCreatedAtDesc(
          String assignedInstance, Collection<TransportKind> providers);

  @EntityGraph(attributePaths = INTEGRATION_SETTINGS)
  List<DesiredSocketSourceEntity> findByIdInAndProviderInAndArchivedAtIsNull(
      Collection<UUID> ids, Collection<TransportKind> providers);
}
