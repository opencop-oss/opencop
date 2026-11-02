// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ResolveCurrentAccountAccessUsecase {

  private final LockedCurrentAccountAccessGateway postgresGateway;
  private final CurrentAccountAccessCacheGateway cacheGateway;
  private final CurrentAccountAccessPolicy policy;
  private final TransactionTemplate transactionTemplate;

  public ResolveCurrentAccountAccessUsecase(
      LockedCurrentAccountAccessGateway postgresGateway,
      CurrentAccountAccessCacheGateway cacheGateway,
      CurrentAccountAccessPolicy policy,
      PlatformTransactionManager transactionManager) {
    this.postgresGateway = postgresGateway;
    this.cacheGateway = cacheGateway;
    this.policy = policy;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public CurrentAccountAccess resolve(UUID internalUserId, long loginSessionGeneration) {
    var access = load(internalUserId);
    policy.requireEligible(access, loginSessionGeneration);
    return access;
  }

  private CurrentAccountAccess load(UUID internalUserId) {
    return cacheGateway
        .findByInternalUserId(internalUserId)
        .orElseGet(
            () -> transactionTemplate.execute(_ -> loadFromPostgresAndPopulate(internalUserId)));
  }

  private CurrentAccountAccess loadFromPostgresAndPopulate(UUID internalUserId) {
    var access =
        postgresGateway
            .findByInternalUserIdForCachePopulation(internalUserId)
            .orElseThrow(
                () ->
                    new CurrentAccountAccessDeniedException(
                        CurrentAccountAccessDenialReason.MISSING_ACCOUNT));
    cacheGateway.populate(access);
    return access;
  }
}
