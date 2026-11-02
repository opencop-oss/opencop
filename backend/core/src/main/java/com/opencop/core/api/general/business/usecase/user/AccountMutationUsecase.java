// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.model.user.AccountMutationNotFoundException;
import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountMutationUsecase {

  private final AccountMutationGateway gateway;
  private final AccountMutationPolicy policy;
  private final CurrentAccountAccessCacheGateway cacheGateway;

  public AccountMutationUsecase(
      AccountMutationGateway gateway,
      AccountMutationPolicy policy,
      CurrentAccountAccessCacheGateway cacheGateway) {
    this.gateway = gateway;
    this.policy = policy;
    this.cacheGateway = cacheGateway;
  }

  @Transactional
  public void setAdministrator(UUID internalUserId, boolean administrator) {
    mutate(internalUserId, account -> policy.withAdministrator(account, administrator));
  }

  @Transactional
  public void create(UUID internalUserId, boolean active) {
    mutate(internalUserId, account -> policy.created(account, active));
  }

  @Transactional
  public void reactivate(UUID internalUserId, String intendedEmail, boolean active) {
    mutate(internalUserId, account -> policy.reactivated(account, intendedEmail, active));
  }

  @Transactional
  public void setActive(UUID internalUserId, boolean active) {
    mutate(internalUserId, account -> policy.withActive(account, active));
  }

  @Transactional
  public void delete(UUID internalUserId, Instant deletedAt) {
    mutate(internalUserId, account -> policy.softDeleted(account, deletedAt));
  }

  private void mutate(UUID internalUserId, UnaryOperator<AccountMutationState> accountMutation) {
    var account =
        gateway
            .findByInternalUserIdForUpdate(internalUserId)
            .orElseThrow(() -> new AccountMutationNotFoundException(internalUserId));
    gateway.saveAndFlush(accountMutation.apply(account));
    cacheGateway.evict(internalUserId);
    cacheGateway.scheduleEvictionAfterCommit(internalUserId);
  }
}
