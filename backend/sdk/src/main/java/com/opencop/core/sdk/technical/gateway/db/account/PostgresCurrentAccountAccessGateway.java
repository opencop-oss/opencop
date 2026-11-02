// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.account;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresCurrentAccountAccessGateway
    implements CurrentAccountAccessGateway, LockedCurrentAccountAccessGateway {

  private final CurrentAccountAccessRepository repository;

  public PostgresCurrentAccountAccessGateway(CurrentAccountAccessRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
    return repository.findByInternalUserId(internalUserId).map(this::toModel);
  }

  @Override
  @Transactional
  public Optional<CurrentAccountAccess> findByInternalUserIdForCachePopulation(
      UUID internalUserId) {
    return repository.findByInternalUserIdForCachePopulation(internalUserId).map(this::toModel);
  }

  private CurrentAccountAccess toModel(CurrentAccountAccessEntity entity) {
    return new CurrentAccountAccess(
        entity.getInternalUserId(),
        entity.isActive(),
        entity.isAdministrator(),
        entity.getSessionGeneration(),
        entity.getAccountRevision());
  }
}
