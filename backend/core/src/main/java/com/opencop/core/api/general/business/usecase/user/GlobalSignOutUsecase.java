// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.business.model.user.AccountMutationNotFoundException;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GlobalSignOutUsecase {

  private final AccountMutationGateway accountGateway;
  private final AccountMutationPolicy accountPolicy;
  private final CurrentAccountAccessCacheGateway cacheGateway;

  public GlobalSignOutUsecase(
      AccountMutationGateway accountGateway,
      AccountMutationPolicy accountPolicy,
      CurrentAccountAccessCacheGateway cacheGateway) {
    this.accountGateway = accountGateway;
    this.accountPolicy = accountPolicy;
    this.cacheGateway = cacheGateway;
  }

  @Transactional
  public void signOut(UUID internalUserId) {
    var account =
        accountGateway
            .findByInternalUserIdForUpdate(internalUserId)
            .orElseThrow(() -> new AccountMutationNotFoundException(internalUserId));
    var signedOutAccount = accountPolicy.signedOut(account);
    accountGateway.saveAndFlush(signedOutAccount);
    cacheGateway.evict(internalUserId);
    cacheGateway.scheduleEvictionAfterCommit(internalUserId);
  }
}
