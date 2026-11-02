// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionLifetimePolicyTest {

  private static final Instant NOW = Instant.parse("2026-07-27T16:00:00Z");

  @Test
  void absoluteLifetimeIsValidUntilButNotAtItsFixedDeadline() {
    var policy = new SessionLifetimePolicy(Duration.ofHours(8), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(
            policy.isAuthenticationCurrent(
                NOW.minus(Duration.ofHours(8)).plusNanos(1), NOW.minus(Duration.ofMinutes(1))))
        .isTrue();
    assertThat(
            policy.isAuthenticationCurrent(
                NOW.minus(Duration.ofHours(8)), NOW.minus(Duration.ofMinutes(1))))
        .isFalse();
  }

  @Test
  void retainedPrincipalWithoutAuthenticationTimeUsesSessionCreationTime() {
    var policy = new SessionLifetimePolicy(Duration.ofHours(8), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(policy.isAuthenticationCurrent(null, NOW.minus(Duration.ofHours(8)).plusNanos(1)))
        .isTrue();
    assertThat(policy.isAuthenticationCurrent(null, NOW.minus(Duration.ofHours(8)))).isFalse();
  }
}
