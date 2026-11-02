// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SessionLifetimePolicy {

  private final Duration absoluteLifetime;
  private final Clock clock;

  public SessionLifetimePolicy(Duration absoluteLifetime, Clock clock) {
    this.absoluteLifetime = Objects.requireNonNull(absoluteLifetime);
    this.clock = Objects.requireNonNull(clock);
  }

  public Instant authenticationDeadline(Instant authenticatedAt, Instant sessionCreationTime) {
    Instant lifetimeStart =
        authenticatedAt != null
            ? authenticatedAt
            : Objects.requireNonNull(sessionCreationTime, "sessionCreationTime");
    return lifetimeStart.plus(absoluteLifetime);
  }

  public boolean isAuthenticationCurrent(Instant authenticatedAt, Instant sessionCreationTime) {
    return clock.instant().isBefore(authenticationDeadline(authenticatedAt, sessionCreationTime));
  }
}
