// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDecision;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import java.util.Optional;

public final class CurrentAccountAccessPolicy {

  public CurrentAccountAccessDecision decide(
      Optional<CurrentAccountAccess> currentAccount, long loginSessionGeneration) {
    if (currentAccount.isEmpty()) {
      return CurrentAccountAccessDecision.MISSING_ACCOUNT;
    }
    CurrentAccountAccess account = currentAccount.orElseThrow();
    if (!account.active()) {
      return CurrentAccountAccessDecision.INACTIVE;
    }
    if (account.sessionGeneration() != loginSessionGeneration) {
      return CurrentAccountAccessDecision.STALE_GENERATION;
    }
    return CurrentAccountAccessDecision.ADMIT;
  }

  public void requireEligible(CurrentAccountAccess access, long loginSessionGeneration) {
    switch (decide(Optional.of(access), loginSessionGeneration)) {
      case ADMIT -> {
        return;
      }
      case INACTIVE ->
          throw new CurrentAccountAccessDeniedException(CurrentAccountAccessDenialReason.INACTIVE);
      case STALE_GENERATION ->
          throw new CurrentAccountAccessDeniedException(
              CurrentAccountAccessDenialReason.STALE_GENERATION);
      case MISSING_ACCOUNT ->
          throw new IllegalStateException("Present account resolved as missing");
    }
  }

  public void requireActive(CurrentAccountAccess access) {
    if (!access.active()) {
      throw new CurrentAccountAccessDeniedException(CurrentAccountAccessDenialReason.INACTIVE);
    }
  }
}
