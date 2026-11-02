// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RedisHistoricalJobLifecycleUpdaterTest {

  private final RedisHistoricalJobLifecycleStateRepository repository =
      Mockito.mock(RedisHistoricalJobLifecycleStateRepository.class);
  private final RedisHistoricalJobLifecycleUpdater updater =
      new RedisHistoricalJobLifecycleUpdater(repository);

  @Test
  void markCompleted_delegatesToRepository() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    Duration ttl = Duration.ofHours(24);
    Mockito.when(repository.markCompleted(jobId, ttl)).thenReturn(true);

    boolean marked = updater.markCompleted(jobId, ttl);

    assertTrue(marked);
    verify(repository).markCompleted(jobId, ttl);
  }

  @Test
  void markFailed_delegatesToRepository() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    Duration ttl = Duration.ofHours(24);
    Mockito.when(repository.markFailed(jobId, "failure", ttl)).thenReturn(true);

    boolean marked = updater.markFailed(jobId, "failure", ttl);

    assertTrue(marked);
    verify(repository).markFailed(jobId, "failure", ttl);
  }
}
