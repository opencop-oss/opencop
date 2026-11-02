// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.reconciliation;

import java.time.Duration;

public record DesiredStateOutagePolicy(
    boolean stopOnDesiredStateUnavailable, Duration desiredStateUnavailableGrace) {

  private static final Duration DEFAULT_GRACE = Duration.ofSeconds(60);

  public DesiredStateOutagePolicy {
    if (desiredStateUnavailableGrace == null || desiredStateUnavailableGrace.isNegative()) {
      desiredStateUnavailableGrace = DEFAULT_GRACE;
    }
  }

  public static DesiredStateOutagePolicy continueExisting() {
    return new DesiredStateOutagePolicy(false, DEFAULT_GRACE);
  }
}
