// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class RedisHistoricalJobLifecycleUpdater implements HistoricalJobLifecycleUpdater {

  private final RedisHistoricalJobLifecycleStateRepository repository;

  public RedisHistoricalJobLifecycleUpdater(RedisHistoricalJobLifecycleStateRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public boolean markCompleted(UUID jobId, Duration ttl) {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(ttl, "ttl");
    return repository.markCompleted(jobId, ttl);
  }

  @Override
  public boolean markFailed(UUID jobId, String failureReason, Duration ttl) {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(ttl, "ttl");
    return repository.markFailed(jobId, failureReason, ttl);
  }
}
