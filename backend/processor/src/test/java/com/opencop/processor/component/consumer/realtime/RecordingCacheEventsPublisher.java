// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.realtime;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RecordingCacheEventsPublisher implements CachePublisher<CacheEventsNotification> {

  private final BlockingQueue<CacheEventsNotification> events = new LinkedBlockingQueue<>();
  private final List<CacheEventsNotification> published = new java.util.ArrayList<>();

  @Override
  public synchronized void publish(CacheEventsNotification event) {
    published.add(event);
    events.add(event);
    notifyAll();
  }

  public CacheEventsNotification awaitPublished() throws InterruptedException {
    var notification = pollPublished(Duration.ofSeconds(10));
    if (notification == null) {
      throw new AssertionError("Timed out waiting for cache events notification");
    }
    return notification;
  }

  public CacheEventsNotification pollPublished(Duration timeout) throws InterruptedException {
    return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  public List<CacheEventsNotification> published() {
    return List.copyOf(published);
  }

  public synchronized List<CacheEventsNotification> awaitExactPublishedCount(
      int expected, Duration timeout, Duration quietPeriod) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (published.size() < expected && System.nanoTime() < deadline) {
      long remainingMs = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
    }
    if (published.size() != expected) {
      throw new AssertionError(
          "Timed out waiting for exactly %d cache notifications, observed %d"
              .formatted(expected, published.size()));
    }

    long quietUntil = System.nanoTime() + quietPeriod.toNanos();
    int observedCount = published.size();
    while (System.nanoTime() < quietUntil) {
      long remainingMs = Duration.ofNanos(quietUntil - System.nanoTime()).toMillis();
      wait(Math.max(1L, remainingMs));
      if (published.size() != observedCount) {
        throw new AssertionError(
            "Expected exactly %d cache notifications but observed %d"
                .formatted(expected, published.size()));
      }
    }

    return List.copyOf(published);
  }

  public synchronized void reset() {
    published.clear();
    events.clear();
  }
}
