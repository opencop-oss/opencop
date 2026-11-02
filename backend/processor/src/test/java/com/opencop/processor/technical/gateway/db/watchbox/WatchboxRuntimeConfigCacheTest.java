// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WatchboxRuntimeConfigCacheTest {

  @Test
  void reloadsAfterTtlAndServesLastSuccessfulSnapshotOnFailure() {
    var calls = new AtomicInteger();
    var first = new WatchboxRuntimeSnapshot(Map.of());
    var second = WatchboxRuntimeSnapshot.empty();
    WatchboxRuntimeConfigGateway gateway =
        () -> {
          var call = calls.incrementAndGet();
          if (call == 1) {
            return first;
          }
          if (call == 2) {
            return second;
          }
          throw new IllegalStateException("database unavailable");
        };
    var ticker = new MutableTicker();
    var registry = new SimpleMeterRegistry();
    var cache =
        new WatchboxRuntimeConfigCache(
            gateway, Duration.ofSeconds(5), new ProcessorMetrics(registry), ticker);

    assertThat(cache.load()).isSameAs(first);
    assertThat(cache.load()).isSameAs(first);
    assertThat(calls).hasValue(1);

    ticker.advance(Duration.ofSeconds(6));
    assertThat(cache.load()).isSameAs(second);
    assertThat(calls).hasValue(2);

    ticker.advance(Duration.ofSeconds(6));
    assertThat(cache.load()).isSameAs(second);
    assertThat(calls).hasValue(3);
    assertThat(cache.load()).isSameAs(second);
    assertThat(calls).hasValue(3);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.load_failures").count())
        .isEqualTo(1.0);
  }

  /**
   * An initial-load failure must fail the caller, not masquerade as feature-off: an empty snapshot
   * here would let the batch listener commit offsets and silently skip events that the
   * infrastructure retry-in-place path exists to hold on to. Each subsequent call retries the load
   * immediately (nothing is cached) until one succeeds.
   */
  @Test
  void propagatesInitialLoadFailureAndRetriesUntilFirstSuccess() {
    var calls = new AtomicInteger();
    var recovered = new WatchboxRuntimeSnapshot(Map.of());
    WatchboxRuntimeConfigGateway gateway =
        () -> {
          if (calls.incrementAndGet() <= 2) {
            throw new IllegalStateException("database unavailable");
          }
          return recovered;
        };
    var registry = new SimpleMeterRegistry();
    var cache =
        new WatchboxRuntimeConfigCache(
            gateway, Duration.ofSeconds(5), new ProcessorMetrics(registry), new MutableTicker());

    assertThatThrownBy(cache::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
    assertThatThrownBy(cache::load).isInstanceOf(IllegalStateException.class);
    assertThat(cache.load()).isSameAs(recovered);
    assertThat(calls).hasValue(3);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.load_failures").count())
        .isEqualTo(2.0);
  }

  @Test
  void generationReloadNeverFallsBackToRejectedLastKnownGood() {
    var calls = new AtomicInteger();
    var rejected = new WatchboxRuntimeSnapshot(Map.of());
    var recovered = WatchboxRuntimeSnapshot.empty();
    WatchboxRuntimeConfigGateway gateway =
        () -> {
          var call = calls.incrementAndGet();
          if (call == 1) {
            return rejected;
          }
          if (call <= 3) {
            throw new IllegalStateException("database unavailable");
          }
          return recovered;
        };
    var registry = new SimpleMeterRegistry();
    var cache =
        new WatchboxRuntimeConfigCache(
            gateway, Duration.ofMinutes(1), new ProcessorMetrics(registry), new MutableTicker());

    assertThat(cache.load()).isSameAs(rejected);
    assertThatThrownBy(() -> cache.reloadAfterGenerationChange(rejected))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
    assertThatThrownBy(cache::load).isInstanceOf(IllegalStateException.class);
    assertThat(cache.load()).isSameAs(recovered);

    assertThat(calls).hasValue(4);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.load_failures").count())
        .isEqualTo(2.0);
  }

  @Test
  void generationReloadReusesSnapshotAnotherListenerAlreadyRefreshed() {
    var calls = new AtomicInteger();
    var rejected = new WatchboxRuntimeSnapshot(Map.of());
    var current = WatchboxRuntimeSnapshot.empty();
    WatchboxRuntimeConfigGateway gateway = () -> calls.incrementAndGet() == 1 ? rejected : current;
    var cache =
        new WatchboxRuntimeConfigCache(
            gateway, Duration.ofMinutes(1), ProcessorMetrics.noop(), new MutableTicker());

    assertThat(cache.load()).isSameAs(rejected);
    assertThat(cache.reloadAfterGenerationChange(rejected)).isSameAs(current);
    assertThat(cache.reloadAfterGenerationChange(rejected)).isSameAs(current);
    cache.invalidateAfterGenerationChange(rejected);
    assertThat(cache.load()).isSameAs(current);
    assertThat(calls).hasValue(2);
  }

  @Test
  void terminalGenerationInvalidationClearsRejectedSnapshotWithoutLoading() {
    var calls = new AtomicInteger();
    var rejected = new WatchboxRuntimeSnapshot(Map.of());
    var recovered = WatchboxRuntimeSnapshot.empty();
    WatchboxRuntimeConfigGateway gateway =
        () -> {
          var call = calls.incrementAndGet();
          if (call == 1) {
            return rejected;
          }
          if (call == 2) {
            throw new IllegalStateException("database unavailable");
          }
          return recovered;
        };
    var cache =
        new WatchboxRuntimeConfigCache(
            gateway, Duration.ofMinutes(1), ProcessorMetrics.noop(), new MutableTicker());

    assertThat(cache.load()).isSameAs(rejected);
    cache.invalidateAfterGenerationChange(rejected);
    assertThat(calls).hasValue(1);
    assertThatThrownBy(cache::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
    assertThat(cache.load()).isSameAs(recovered);
    assertThat(calls).hasValue(3);
  }

  private static final class MutableTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong();

    @Override
    public long read() {
      return nanos.get();
    }

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }
  }
}
