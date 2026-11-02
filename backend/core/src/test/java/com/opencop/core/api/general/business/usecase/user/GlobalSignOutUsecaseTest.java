// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GlobalSignOutUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("8eed225a-7938-49e1-b303-c8aa1ef57a31");

  @Test
  void signOutAdvancesGenerationWhileKeepingAccountActiveAndEvictingCachedAccess() {
    var steps = new ArrayList<String>();
    var accountGateway = new InMemoryAccountMutationGateway(steps);
    var cacheGateway = new RecordingCacheGateway(steps);
    var usecase =
        new GlobalSignOutUsecase(
            accountGateway, new AccountMutationPolicy("admin@example.com"), cacheGateway);

    usecase.signOut(USER_ID);

    assertTrue(accountGateway.account.active());
    assertEquals(8, accountGateway.account.sessionGeneration());
    assertEquals(12, accountGateway.account.accountRevision());
    assertEquals(List.of("lock", "flush", "required eviction", "post-commit eviction"), steps);
  }

  private static final class InMemoryAccountMutationGateway implements AccountMutationGateway {

    private final List<String> steps;
    private AccountMutationState account =
        new AccountMutationState(USER_ID, "user@example.com", true, false, 7, 11, null);

    private InMemoryAccountMutationGateway(List<String> steps) {
      this.steps = steps;
    }

    @Override
    public Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId) {
      steps.add("lock");
      return USER_ID.equals(internalUserId) ? Optional.of(account) : Optional.empty();
    }

    @Override
    public void saveAndFlush(AccountMutationState account) {
      steps.add("flush");
      this.account = account;
    }
  }

  private record RecordingCacheGateway(List<String> steps)
      implements CurrentAccountAccessCacheGateway {

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      return Optional.empty();
    }

    @Override
    public void populate(CurrentAccountAccess access) {}

    @Override
    public void evict(UUID internalUserId) {
      steps.add("required eviction");
    }

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {
      steps.add("post-commit eviction");
    }
  }
}
