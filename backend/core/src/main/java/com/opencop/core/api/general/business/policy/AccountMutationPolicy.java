// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.user.AccountAlreadyActiveException;
import com.opencop.core.api.general.business.model.user.AccountMutationState;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountMutationPolicy {

  private final String defaultAdministratorEmail;

  public AccountMutationPolicy(
      @Value("${opencop.admin.default:}") String defaultAdministratorEmail) {
    this.defaultAdministratorEmail = defaultAdministratorEmail;
  }

  public AccountMutationState withAdministrator(
      AccountMutationState account, boolean administrator) {
    return copy(account, account.active(), administrator, account.deletedAt());
  }

  public AccountMutationState created(AccountMutationState account, boolean active) {
    return createdOrReactivated(account, account.email(), active);
  }

  public AccountMutationState reactivated(
      AccountMutationState account, String intendedEmail, boolean active) {
    if (account.active()) {
      throw new AccountAlreadyActiveException(account.internalUserId());
    }
    return createdOrReactivated(account, intendedEmail, active);
  }

  private AccountMutationState createdOrReactivated(
      AccountMutationState account, String intendedEmail, boolean active) {
    boolean administrator =
        account.administrator()
            || (intendedEmail != null && intendedEmail.equals(defaultAdministratorEmail));
    return copy(account, active, administrator, null);
  }

  public AccountMutationState withActive(AccountMutationState account, boolean active) {
    if (account.active() && !active) {
      return copyWithRevokedSessions(account, false, account.administrator(), account.deletedAt());
    }
    return copy(account, active, account.administrator(), account.deletedAt());
  }

  public AccountMutationState softDeleted(AccountMutationState account, Instant deletedAt) {
    return copyWithRevokedSessions(account, false, account.administrator(), deletedAt);
  }

  public AccountMutationState signedOut(AccountMutationState account) {
    return copyWithRevokedSessions(
        account, account.active(), account.administrator(), account.deletedAt());
  }

  private static AccountMutationState copy(
      AccountMutationState account, boolean active, boolean administrator, Instant deletedAt) {
    return copy(account, active, administrator, account.sessionGeneration(), deletedAt);
  }

  private static AccountMutationState copyWithRevokedSessions(
      AccountMutationState account, boolean active, boolean administrator, Instant deletedAt) {
    return copy(
        account,
        active,
        administrator,
        Math.incrementExact(account.sessionGeneration()),
        deletedAt);
  }

  private static AccountMutationState copy(
      AccountMutationState account,
      boolean active,
      boolean administrator,
      long sessionGeneration,
      Instant deletedAt) {
    return new AccountMutationState(
        account.internalUserId(),
        account.email(),
        active,
        administrator,
        sessionGeneration,
        Math.incrementExact(account.accountRevision()),
        deletedAt);
  }
}
