// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDecision;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentAccountAccessPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554410a1");

  @Test
  void admitsAnActiveAccountAtTheLoginSessionGeneration() {
    var policy = new CurrentAccountAccessPolicy();
    var account = new CurrentAccountAccess(USER_ID, true, false, 17, 4);

    assertThat(policy.decide(Optional.of(account), 17))
        .isEqualTo(CurrentAccountAccessDecision.ADMIT);
  }

  @Test
  void deniesAMissingAccount() {
    var policy = new CurrentAccountAccessPolicy();

    assertThat(policy.decide(Optional.empty(), 17))
        .isEqualTo(CurrentAccountAccessDecision.MISSING_ACCOUNT);
  }

  @Test
  void deniesAnInactiveAccount() {
    var policy = new CurrentAccountAccessPolicy();
    var account = new CurrentAccountAccess(USER_ID, false, false, 17, 4);

    assertThat(policy.decide(Optional.of(account), 17))
        .isEqualTo(CurrentAccountAccessDecision.INACTIVE);
  }

  @Test
  void deniesAStaleLoginSessionGeneration() {
    var policy = new CurrentAccountAccessPolicy();
    var account = new CurrentAccountAccess(USER_ID, true, false, 18, 4);

    assertThat(policy.decide(Optional.of(account), 17))
        .isEqualTo(CurrentAccountAccessDecision.STALE_GENERATION);
  }
}
