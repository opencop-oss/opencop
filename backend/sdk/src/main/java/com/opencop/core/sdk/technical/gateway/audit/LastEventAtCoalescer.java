// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.audit;

import com.opencop.core.sdk.ports.gateway.audit.LastEventAtRecorder;
import com.opencop.core.sdk.ports.gateway.audit.LastEventAtWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Collapses high-frequency "this resource was used" observations into at most one database write
 * per resource per flush interval.
 *
 * <p>{@code last_event_at} answers a staleness question measured in days, but its triggers fire per
 * decoded message and per detail-panel poll. Writing through on every observation would put a
 * durable metadata update on a hot path for precision nobody reads, so observations are merged in
 * memory and flushed on a timer.
 *
 * <p>Latest-wins: {@link #record} keeps the later of the pending and incoming instants, so a flush
 * never moves a resource backwards even when observations arrive out of order.
 *
 * <p>Best-effort by contract. A failed flush keeps its observation pending and retries on the next
 * interval; a flush rejected as a terminal integrity violation is dropped rather than retried
 * forever. Losing an observation costs at most one interval of resolution on a column whose
 * consumers think in months, so no caller should treat a write failure as an error worth
 * propagating.
 *
 * @see LastEventAtWriter for the monotonic-write requirement implementations must honor
 */
public final class LastEventAtCoalescer implements LastEventAtRecorder, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LastEventAtCoalescer.class);

  /**
   * Shared default so every service coalescing last-event writes uses the same cadence. Tuning it
   * in one deployment while another keeps the old value would make the same column advance at two
   * different rates depending on which service happened to observe the use.
   *
   * <p>Matches Connect's heartbeat interval, which is the one cadence this feature does not choose
   * — data-source activity is promoted from an observation row that heartbeat writes — so every
   * resource advances on the same beat. It is a volume dial rather than a precision requirement:
   * nothing reading a months-scale staleness column can perceive the difference, so it can be
   * lengthened freely if write volume ever justifies it.
   */
  public static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(15);

  /** How long {@link #close()} waits for an in-flight flush before giving up on it. */
  private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

  private final String name;
  private final LastEventAtWriter writer;
  private final Duration flushInterval;
  private final Clock clock;
  private final ScheduledExecutorService scheduler;
  private final Runnable beforePendingCleanup;
  private final AtomicBoolean started = new AtomicBoolean(false);

  /** Latest observed instant per resource awaiting a flush. */
  private final ConcurrentHashMap<UUID, AtomicReference<Instant>> pending =
      new ConcurrentHashMap<>();

  private final ConcurrentHashMap<UUID, Instant> lastSuccessfulFlushAt = new ConcurrentHashMap<>();

  public LastEventAtCoalescer(String name, LastEventAtWriter writer, Duration flushInterval) {
    this(
        name,
        writer,
        flushInterval,
        Clock.systemUTC(),
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              var thread = new Thread(runnable, name + "-last-event-flush");
              thread.setDaemon(true);
              return thread;
            }),
        () -> {});
  }

  /**
   * A coalescer with no background timer, flushed only when the caller says so.
   *
   * <p>For test contexts that need the real merge-and-write behavior but must not race a scheduled
   * flush: without this, a caller assembling one through the public constructor gets a live
   * executor that happens to stay idle only because nothing calls {@link #start()}.
   */
  public static LastEventAtCoalescer withoutScheduler(
      String name, LastEventAtWriter writer, Duration flushInterval) {
    return new LastEventAtCoalescer(name, writer, flushInterval, Clock.systemUTC(), null, () -> {});
  }

  // Package-private for deterministic tests; production uses the public constructor.
  LastEventAtCoalescer(
      String name,
      LastEventAtWriter writer,
      Duration flushInterval,
      Clock clock,
      ScheduledExecutorService scheduler,
      Runnable beforePendingCleanup) {
    this.name = Objects.requireNonNull(name, "name");
    this.writer = Objects.requireNonNull(writer, "writer");
    this.flushInterval = positiveOrDefault(flushInterval);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.scheduler = scheduler;
    this.beforePendingCleanup =
        Objects.requireNonNull(beforePendingCleanup, "beforePendingCleanup");
  }

  /** Starts the flush timer. Idempotent; a null scheduler leaves the coalescer flush-on-demand. */
  public void start() {
    if (scheduler == null || !started.compareAndSet(false, true)) {
      return;
    }
    log.info("Starting {} last-event coalescer with flush interval {}", name, flushInterval);
    scheduler.scheduleAtFixedRate(
        this::flushDueSafely,
        flushInterval.toMillis(),
        flushInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    flushAll();
    if (scheduler == null) {
      return;
    }
    scheduler.shutdown();
    try {
      // A flush already running when shutdown() was called keeps its database connection open.
      // Returning before it finishes would let the surrounding context tear that connection down
      // underneath it, turning an orderly shutdown into a spurious error in the logs.
      if (!scheduler.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
        log.debug("{} last-event flush did not finish within {}", name, SHUTDOWN_GRACE);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Notes that {@code resourceId} was used at {@code observedAt}. Never throws for valid input. */
  @Override
  public void record(UUID resourceId, Instant observedAt) {
    Objects.requireNonNull(resourceId, "resourceId");
    Objects.requireNonNull(observedAt, "observedAt");
    pending
        .computeIfAbsent(resourceId, _ -> new AtomicReference<>())
        .updateAndGet(
            current -> current == null || observedAt.isAfter(current) ? observedAt : current);
  }

  /** Flushes every pending resource regardless of when it was last written. */
  public void flushAll() {
    for (var resourceId : pending.keySet()) {
      flush(resourceId, true);
    }
  }

  // Visible for testing: drives one scheduler tick without waiting on wall-clock time.
  void flushDue() {
    for (var resourceId : pending.keySet()) {
      flush(resourceId, false);
    }
  }

  private void flushDueSafely() {
    try {
      flushDue();
    } catch (Throwable t) {
      log.warn("{} last-event flush failed unexpectedly", name, t);
    }
  }

  private void flush(UUID resourceId, boolean force) {
    var latestRef = pending.get(resourceId);
    if (latestRef == null) {
      return;
    }
    var latest = latestRef.get();
    if (latest == null) {
      removeIfStillEmpty(resourceId, latestRef);
      return;
    }
    if (!force && !isFlushDue(resourceId)) {
      return;
    }

    try {
      writer.write(resourceId, latest);
      lastSuccessfulFlushAt.put(resourceId, clock.instant());
      if (latestRef.compareAndSet(latest, null)) {
        removeIfStillEmpty(resourceId, latestRef);
      }
    } catch (DataIntegrityViolationException e) {
      // Defensive, and currently unreachable for the writers wired to this class: both issue a
      // plain UPDATE against an unconstrained column, where a missing row returns zero rows rather
      // than raising. Kept for writers that can genuinely reject a vanished resource — an upsert
      // against a foreign key, say — since retrying those forever would leak the pending entry.
      pending.remove(resourceId, latestRef);
      lastSuccessfulFlushAt.remove(resourceId);
      log.debug(
          "Dropping {} last-event observation for {} after terminal integrity failure: {}",
          name,
          resourceId,
          e.getMessage());
    } catch (Exception e) {
      // Left pending deliberately: the next interval retries.
      log.warn("{} last-event write failed for {}; will retry on the next flush", name, resourceId);
      log.debug("{} last-event write failed for {}: {}", name, resourceId, e.getMessage(), e);
    }
  }

  private void removeIfStillEmpty(UUID resourceId, AtomicReference<Instant> latestRef) {
    beforePendingCleanup.run();
    pending.computeIfPresent(
        resourceId,
        (_, currentRef) -> currentRef == latestRef && currentRef.get() == null ? null : currentRef);
  }

  private boolean isFlushDue(UUID resourceId) {
    var lastFlush = lastSuccessfulFlushAt.get(resourceId);
    return lastFlush == null || !clock.instant().minus(flushInterval).isBefore(lastFlush);
  }

  private static Duration positiveOrDefault(Duration value) {
    return value == null || value.isZero() || value.isNegative() ? DEFAULT_FLUSH_INTERVAL : value;
  }
}
