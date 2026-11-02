// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.business.model.user.AccountAlreadyActiveException;
import com.opencop.core.api.general.business.model.user.AccountMutationNotFoundException;
import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountMutationUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("fa2ed771-8094-49e8-a6af-fcbf0c4748c9");
  private static final String DEFAULT_ADMINISTRATOR_EMAIL = "alpha@example.com";

  @Test
  void setAdministrator_updatesOnlyAdministratorState() {
    var deletedAt = Instant.parse("2026-07-20T12:00:00Z");
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(USER_ID, "other@example.com", false, false, 7, 11, deletedAt));
    var usecase = usecase(gateway);

    usecase.setAdministrator(USER_ID, true);

    var saved = gateway.require(USER_ID);
    assertTrue(saved.administrator());
    assertFalse(saved.active());
    assertEquals(7, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
    assertEquals(deletedAt, saved.deletedAt());
  }

  @Test
  void reactivate_whenLockedAccountIsInactive_clearsDeletionAndAppliesRequestedAccessState() {
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(
                USER_ID,
                "old@example.com",
                false,
                false,
                7,
                11,
                Instant.parse("2026-07-20T12:00:00Z")));
    var usecase = usecase(gateway);

    usecase.reactivate(USER_ID, DEFAULT_ADMINISTRATOR_EMAIL, true);

    var saved = gateway.require(USER_ID);
    assertTrue(saved.active());
    assertTrue(saved.administrator());
    assertNull(saved.deletedAt());
    assertEquals(7, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void reactivate_whenIntendedEmailIsNotDefault_doesNotGrantAdministratorFromLockedOldEmail() {
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(
                USER_ID,
                DEFAULT_ADMINISTRATOR_EMAIL,
                false,
                false,
                7,
                11,
                Instant.parse("2026-07-20T12:00:00Z")));
    var usecase = usecase(gateway);

    usecase.reactivate(USER_ID, "intended@example.com", true);

    var saved = gateway.require(USER_ID);
    assertTrue(saved.active());
    assertFalse(saved.administrator());
    assertNull(saved.deletedAt());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void reactivate_whenLockedAccountBecameActive_failsWithoutMutationOrPropagation() {
    var steps = new ArrayList<String>();
    var original = new AccountMutationState(USER_ID, "other@example.com", true, false, 7, 11, null);
    var gateway = new InMemoryAccountMutationGateway(steps, original);
    var usecase = usecase(gateway, new RecordingCurrentAccountAccessCacheGateway(steps));

    assertThrows(
        AccountAlreadyActiveException.class,
        () -> usecase.reactivate(USER_ID, "intended@example.com", true));

    assertEquals(original, gateway.require(USER_ID));
    assertEquals(List.of("lock"), steps);
  }

  @Test
  void setActive_whenDeactivating_advancesGenerationAndPreservesOtherAccountState() {
    var deletedAt = Instant.parse("2026-07-20T12:00:00Z");
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(USER_ID, "other@example.com", true, true, 7, 11, deletedAt));
    var usecase = usecase(gateway);

    usecase.setActive(USER_ID, false);

    var saved = gateway.require(USER_ID);
    assertFalse(saved.active());
    assertTrue(saved.administrator());
    assertEquals(deletedAt, saved.deletedAt());
    assertEquals(8, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void setActive_whenActivating_preservesSessionGeneration() {
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(USER_ID, "other@example.com", false, true, 7, 11, null));
    var usecase = usecase(gateway);

    usecase.setActive(USER_ID, true);

    var saved = gateway.require(USER_ID);
    assertTrue(saved.active());
    assertEquals(7, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void delete_deactivatesAndRecordsDeletionTime() {
    var deletedAt = Instant.parse("2026-07-21T12:00:00Z");
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(USER_ID, "other@example.com", true, true, 7, 11, null));
    var usecase = usecase(gateway);

    usecase.delete(USER_ID, deletedAt);

    var saved = gateway.require(USER_ID);
    assertFalse(saved.active());
    assertTrue(saved.administrator());
    assertEquals(deletedAt, saved.deletedAt());
    assertEquals(8, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void delete_whenAlreadyInactive_stillAdvancesGeneration() {
    var deletedAt = Instant.parse("2026-07-21T12:00:00Z");
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(USER_ID, "other@example.com", false, true, 7, 11, null));
    var usecase = usecase(gateway);

    usecase.delete(USER_ID, deletedAt);

    var saved = gateway.require(USER_ID);
    assertFalse(saved.active());
    assertEquals(8, saved.sessionGeneration());
    assertEquals(12, saved.accountRevision());
  }

  @Test
  void create_withoutDefaultGrant_preservesExistingAdministratorState() {
    var gateway =
        new InMemoryAccountMutationGateway(
            new AccountMutationState(
                USER_ID,
                "other@example.com",
                false,
                true,
                7,
                11,
                Instant.parse("2026-07-20T12:00:00Z")));
    var usecase = usecase(gateway);

    usecase.create(USER_ID, false);

    var saved = gateway.require(USER_ID);
    assertFalse(saved.active());
    assertTrue(saved.administrator());
    assertNull(saved.deletedAt());
  }

  @Test
  void mutationOfMissingAccount_failsWithoutCreatingState() {
    var gateway = new InMemoryAccountMutationGateway();
    var usecase = usecase(gateway);

    assertThrows(
        AccountMutationNotFoundException.class, () -> usecase.setAdministrator(USER_ID, true));

    assertFalse(gateway.contains(USER_ID));
  }

  @Test
  void mutation_locksThenFlushesBeforeRequiredAndPostCommitEvictions() {
    var steps = new ArrayList<String>();
    var gateway =
        new InMemoryAccountMutationGateway(
            steps,
            new AccountMutationState(USER_ID, "other@example.com", true, false, 7, 11, null));
    var cacheGateway = new RecordingCurrentAccountAccessCacheGateway(steps);
    var usecase = usecase(gateway, cacheGateway);

    usecase.setAdministrator(USER_ID, true);

    assertEquals(List.of("lock", "flush", "required eviction", "post-commit eviction"), steps);
  }

  @Test
  void deactivationFlushesAndEvictsCurrentAccountAccess() {
    var steps = new ArrayList<String>();
    var gateway =
        new InMemoryAccountMutationGateway(
            steps,
            new AccountMutationState(USER_ID, "other@example.com", true, false, 7, 11, null));
    var cacheGateway = new RecordingCurrentAccountAccessCacheGateway(steps);
    var usecase = usecase(gateway, cacheGateway);

    usecase.setActive(USER_ID, false);

    assertEquals(List.of("lock", "flush", "required eviction", "post-commit eviction"), steps);
  }

  @Test
  void requiredEvictionFailure_isPropagatedWithoutSchedulingPostCommitEviction() {
    var steps = new ArrayList<String>();
    var gateway =
        new InMemoryAccountMutationGateway(
            steps,
            new AccountMutationState(USER_ID, "other@example.com", true, false, 7, 11, null));
    var cacheGateway =
        new CurrentAccountAccessCacheGateway() {
          @Override
          public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
            return Optional.empty();
          }

          @Override
          public void populate(CurrentAccountAccess access) {}

          @Override
          public void evict(UUID internalUserId) {
            steps.add("required eviction");
            throw new IllegalStateException("Redis unavailable");
          }

          @Override
          public void scheduleEvictionAfterCommit(UUID internalUserId) {
            steps.add("post-commit eviction");
          }
        };
    var usecase = usecase(gateway, cacheGateway);

    assertThrows(IllegalStateException.class, () -> usecase.setAdministrator(USER_ID, true));

    assertEquals(List.of("lock", "flush", "required eviction"), steps);
  }

  private static AccountMutationUsecase usecase(AccountMutationGateway gateway) {
    return usecase(gateway, new RecordingCurrentAccountAccessCacheGateway(new ArrayList<>()));
  }

  private static AccountMutationUsecase usecase(
      AccountMutationGateway gateway, CurrentAccountAccessCacheGateway cacheGateway) {
    return new AccountMutationUsecase(
        gateway, new AccountMutationPolicy(DEFAULT_ADMINISTRATOR_EMAIL), cacheGateway);
  }

  private static final class InMemoryAccountMutationGateway implements AccountMutationGateway {

    private final Map<UUID, AccountMutationState> accounts = new HashMap<>();
    private final List<String> steps;

    private InMemoryAccountMutationGateway(AccountMutationState... initialAccounts) {
      this(new ArrayList<>(), initialAccounts);
    }

    private InMemoryAccountMutationGateway(
        List<String> steps, AccountMutationState... initialAccounts) {
      this.steps = steps;
      for (var account : initialAccounts) {
        accounts.put(account.internalUserId(), account);
      }
    }

    @Override
    public Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId) {
      steps.add("lock");
      return Optional.ofNullable(accounts.get(internalUserId));
    }

    @Override
    public void saveAndFlush(AccountMutationState account) {
      steps.add("flush");
      accounts.put(account.internalUserId(), account);
    }

    private boolean contains(UUID internalUserId) {
      return accounts.containsKey(internalUserId);
    }

    private AccountMutationState require(UUID internalUserId) {
      return accounts.get(internalUserId);
    }
  }

  private record RecordingCurrentAccountAccessCacheGateway(List<String> steps)
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
