// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Objects;

/**
 * Caches the Watchbox runtime snapshot for a short TTL.
 *
 * <p>Availability behavior is <em>last-known-good after first success</em>, not universally
 * fail-closed, and the distinction is operationally significant:
 *
 * <ul>
 *   <li>If the <em>first</em> load fails, there is no prior snapshot and the failure propagates to
 *       the caller. An empty snapshot here would be indistinguishable from feature-off: the event
 *       handler would return normally, the batch listener would commit offsets, and the events
 *       would be skipped without ever reaching the error handler — silent permanent loss, which is
 *       exactly what the listener's retained-batch retry policy exists to prevent. Failing the
 *       batch instead lets that machinery hold the events until the load succeeds.
 *   <li>Once a load has succeeded, a later failed refresh serves the last successful snapshot and
 *       caches it for the full TTL before retrying.
 * </ul>
 *
 * <p>The database generation fence is the exception to that fallback policy. Once the database
 * rejects a cached snapshot as stale, that snapshot is removed from both cache tiers and a failed
 * reload propagates; knowingly retrying the rejected generation would permit stale writes.
 *
 * <p>The consequence to be aware of: while loads keep failing, detection continues against stale
 * configuration, so an administrator's attempted disable is not observed until a load succeeds. A
 * total database outage is largely moot because detections cannot be persisted either; the case
 * that matters is a transient failure of this query alone — a statement timeout, for example —
 * while the database is otherwise healthy.
 *
 * <p>Every failed load increments {@code opencop.watchbox.pipeline.config.load_failures}. Alert on
 * it if the flag is being relied on as a prompt off switch. Bounding how long a stale snapshot may
 * be served is a deliberate follow-up, not an oversight.
 */
public final class WatchboxRuntimeConfigCache implements WatchboxRuntimeConfigGateway {

  private static final String CACHE_KEY = "snapshot";
  private static final String LOAD_FAILURE_METRIC =
      "opencop.watchbox.pipeline.config.load_failures";

  private final WatchboxRuntimeConfigGateway gateway;
  private final ProcessorMetrics metrics;
  private final Cache<String, WatchboxRuntimeSnapshot> cache;
  private volatile WatchboxRuntimeSnapshot lastSuccessful;

  public WatchboxRuntimeConfigCache(
      WatchboxRuntimeConfigGateway gateway, Duration cacheTtl, ProcessorMetrics metrics) {
    this(gateway, cacheTtl, metrics, Ticker.systemTicker());
  }

  WatchboxRuntimeConfigCache(
      WatchboxRuntimeConfigGateway gateway,
      Duration cacheTtl,
      ProcessorMetrics metrics,
      Ticker ticker) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    Objects.requireNonNull(cacheTtl, "cacheTtl");
    if (cacheTtl.isZero() || cacheTtl.isNegative()) {
      throw new IllegalArgumentException("cacheTtl must be positive");
    }
    cache =
        Caffeine.newBuilder()
            .expireAfterWrite(cacheTtl)
            .maximumSize(1)
            .ticker(Objects.requireNonNull(ticker, "ticker"))
            .build();
  }

  // synchronized = single-flight: on TTL expiry exactly one caller reloads while concurrent
  // listener threads block briefly, so they cannot stampede the DB with parallel loads.
  @Override
  public synchronized WatchboxRuntimeSnapshot load() {
    var current = cache.getIfPresent(CACHE_KEY);
    if (current != null) {
      return current;
    }
    try {
      var loaded = Objects.requireNonNull(gateway.load(), "gateway.load()");
      lastSuccessful = loaded;
      cache.put(CACHE_KEY, loaded);
      return loaded;
    } catch (RuntimeException exception) {
      metrics.incrementCounter(LOAD_FAILURE_METRIC, Tags.empty());
      if (lastSuccessful == null) {
        throw exception;
      }
      cache.put(CACHE_KEY, lastSuccessful);
      return lastSuccessful;
    }
  }

  /**
   * Invalidates only the snapshot the database rejected. If another listener already refreshed it,
   * reuse that newer snapshot rather than issuing another load. Clearing last-known-good is
   * mandatory when the rejected snapshot is still current: falling back to it would defeat the
   * database generation fence and repeat the stale write attempt.
   */
  @Override
  public synchronized WatchboxRuntimeSnapshot reloadAfterGenerationChange(
      WatchboxRuntimeSnapshot rejectedSnapshot) {
    Objects.requireNonNull(rejectedSnapshot, "rejectedSnapshot");
    var current = cache.getIfPresent(CACHE_KEY);
    if (current != null && current != rejectedSnapshot) {
      return current;
    }
    invalidateAfterGenerationChange(rejectedSnapshot);
    return load();
  }

  @Override
  public synchronized void invalidateAfterGenerationChange(
      WatchboxRuntimeSnapshot rejectedSnapshot) {
    Objects.requireNonNull(rejectedSnapshot, "rejectedSnapshot");
    if (cache.getIfPresent(CACHE_KEY) == rejectedSnapshot) {
      cache.invalidate(CACHE_KEY);
    }
    if (lastSuccessful == rejectedSnapshot) {
      lastSuccessful = null;
    }
  }
}
