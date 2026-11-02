// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.cachehydration;

import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;

public class RecordingGeoCacheGateway implements GeoCacheGateway {

  private final List<WriteInvocation> writes = new ArrayList<>();

  @Override
  public synchronized void write(List<Feature> inputs, boolean publishUpdates) {
    writes.add(new WriteInvocation(List.copyOf(inputs), publishUpdates));
    notifyAll();
  }

  @Override
  public void clearSource(UUID sourceId) {
    throw new UnsupportedOperationException("Hydration flow should not clear Redis");
  }

  @Override
  public void evictObject(UUID sourceId, String objectId, long ifNotNewerThanMs) {
    throw new UnsupportedOperationException("Hydration flow should not evict from Redis");
  }

  public synchronized List<WriteInvocation> awaitExactWriteCount(
      int expected, Duration timeout, Duration quietPeriod) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (writes.size() < expected && System.nanoTime() < deadline) {
      long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
    }
    if (writes.size() != expected) {
      throw new AssertionError(
          "Timed out waiting for exactly %d cache writes, observed %d"
              .formatted(expected, writes.size()));
    }

    long quietUntil = System.nanoTime() + quietPeriod.toNanos();
    int observed = writes.size();
    while (System.nanoTime() < quietUntil) {
      long remainingMs = Duration.ofNanos(quietUntil - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
      if (writes.size() != observed) {
        throw new AssertionError(
            "Expected exactly %d cache writes but observed %d".formatted(expected, writes.size()));
      }
    }
    return List.copyOf(writes);
  }

  public synchronized void reset() {
    writes.clear();
  }

  public synchronized boolean awaitWriteCountEventually(int expected, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (writes.size() == expected) {
        return true;
      }
      try {
        long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
        wait(Math.max(1L, remainingMs));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for cache writes", e);
      }
    }
    return writes.size() == expected;
  }

  public record WriteInvocation(List<Feature> features, boolean publishUpdates) {}
}
