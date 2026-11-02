// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import java.time.Duration;
import java.util.UUID;

public class NoOpHistoricalJobLifecycleUpdater implements HistoricalJobLifecycleUpdater {

  @Override
  public boolean markCompleted(UUID jobId, Duration ttl) {
    return false;
  }

  @Override
  public boolean markFailed(UUID jobId, String failureReason, Duration ttl) {
    return false;
  }
}
