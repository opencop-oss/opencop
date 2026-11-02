// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PostgresAccountMutationGateway implements AccountMutationGateway {

  private final InternalUserRepository repository;

  public PostgresAccountMutationGateway(InternalUserRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId) {
    var lockedAccount = repository.lockAccountMutationRowWithoutAutoFlush(internalUserId);
    if (lockedAccount.isEmpty()) {
      return Optional.empty();
    }

    return repository
        .findLockedAccountMutationStateWithoutAutoFlush(internalUserId)
        .map(PostgresAccountMutationGateway::toState);
  }

  @Override
  public void saveAndFlush(AccountMutationState account) {
    var entity = repository.getReferenceById(account.internalUserId());
    entity.setActive(account.active());
    entity.setAdministrator(account.administrator());
    entity.setSessionGeneration(account.sessionGeneration());
    entity.setAccountAccessRevision(account.accountRevision());
    entity.setDeletedAt(account.deletedAt());
    repository.saveAndFlush(entity);
  }

  private static AccountMutationState toState(AccountMutationProjection projection) {
    return new AccountMutationState(
        projection.getInternalUserId(),
        projection.getEmail(),
        projection.isActive(),
        projection.isAdministrator(),
        projection.getSessionGeneration(),
        projection.getAccountRevision(),
        projection.getDeletedAt());
  }
}
