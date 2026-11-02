// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertDrainSignal;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single in-process drain owner for the watchbox alert outbox. One daemon thread loops: claim a
 * batch {@code FOR UPDATE SKIP LOCKED}, fence it against the parent Watchbox generation, publish
 * each current live event to the Redis WATCHBOX_ALERTS channel, delete the claimed rows, commit —
 * publish and delete share one transaction, making committed Redis publication work durable and
 * retryable. A crash between Redis publication and commit can publish the same event again on the
 * next drain; every redelivery carries the same stable {@code eventId}, so consumers deduplicate
 * repeated Redis publications (no server-side dedupe exists by design). A publish failure rolls the
 * whole claim back and the fallback interval acts as the retry backoff.
 *
 * <p>This does not make browser receipt at-least-once. Redis Pub/Sub, the WebSocket fan-out, and
 * browsers retain no alert backlog and exchange neither acknowledgement nor replay, so live browser
 * delivery is best-effort.
 *
 * <p>Alerts are delivered live or not at all: a claimed row whose event time has aged past the
 * delivery window is deleted without publishing, counted by {@code
 * opencop.watchbox.alerts.expired}. Subscribers therefore see no stale flood after an outage, and
 * the backlog stays bounded at roughly event rate × window. Expiry involves no publish call, so a
 * backlog that has fully aged out drains even while Redis is still down; {@code watchbox_detection}
 * remains the durable record of every accepted detection either way. The {@code
 * opencop.watchbox.alerts.outbox.oldest.age.seconds} gauge tracks the oldest pending row as a
 * liveness signal: healthy steady state sits near zero, an age far past the delivery window means
 * every drainer is wedged or starved, and a negative value means this instance's drain thread has
 * exited (see {@link #DRAINER_DEAD_AGE_SENTINEL}).
 *
 * <p>Latency: {@link #requestDrain} is an in-process wake-up, so the instance that commits
 * detections drains its own outbox work milliseconds later — in every topology, because each
 * instance runs its own drainer. The fallback poll is not the normal delivery path; it is the
 * recovery bound for rows orphaned by a crash between commit and nudge. Removing the nudge would
 * degrade ordinary alert latency to the poll interval.
 *
 * <p>The thread drains once at startup (crash-recovery rows must not wait for a nudge that died
 * with the previous process), then alternates between coalesced {@link #requestDrain} nudges and
 * the fallback poll. Deliberately not gated on {@code ProcessorStartupLeader}: SKIP LOCKED already
 * coordinates concurrent drainers, and alert delivery must survive the startup leader dying.
 */
public final class WatchboxAlertOutboxDrainer implements WatchboxAlertDrainSignal {

  /**
   * Drain tuning ({@code opencop.watchbox.alerts.drain}) plus the delivery freshness bound. The
   * bound is wired to the watchbox state live window so "too old to admit" and "too old to deliver"
   * stay one definition of live.
   */
  public record Settings(int batchSize, Duration fallbackPoll, Duration deliveryWindow) {
    public Settings {
      if (batchSize < 1) {
        throw new IllegalArgumentException("batchSize must be at least 1");
      }
      requirePositive(fallbackPoll, "fallbackPoll");
      requirePositive(deliveryWindow, "deliveryWindow");
    }

    private static void requirePositive(Duration duration, String name) {
      Objects.requireNonNull(duration, name);
      if (duration.isZero() || duration.isNegative()) {
        throw new IllegalArgumentException(name + " must be positive");
      }
    }
  }

  /** Written to the oldest-age gauge when the drain thread exits; see {@link #guardedRunLoop}. */
  static final long DRAINER_DEAD_AGE_SENTINEL = -1;

  static final String PUBLISHED_METRIC = "opencop.watchbox.alerts.published";
  static final String EXPIRED_METRIC = "opencop.watchbox.alerts.expired";
  static final String DRAIN_FAILURES_METRIC = "opencop.watchbox.alerts.drain.failures";
  static final String OLDEST_AGE_METRIC = "opencop.watchbox.alerts.outbox.oldest.age.seconds";

  private static final Logger log = LoggerFactory.getLogger(WatchboxAlertOutboxDrainer.class);

  private final WatchboxAlertOutboxRepository outboxRepository;
  private final CachePublisher<WatchboxAlertEvent> publisher;
  private final TransactionTemplate transactionTemplate;
  private final Settings settings;
  private final Clock clock;
  private final ProcessorMetrics metrics;
  // Capacity 1 coalesces bursts: a nudge during an active drain schedules exactly one more pass.
  private final BlockingQueue<Boolean> nudge = new ArrayBlockingQueue<>(1);
  // Refreshed by the drain loop after every pass; the class javadoc describes the semantics.
  private final AtomicLong oldestAgeSeconds = new AtomicLong();
  private final ExecutorService executor;

  public WatchboxAlertOutboxDrainer(
      WatchboxAlertOutboxRepository outboxRepository,
      CachePublisher<WatchboxAlertEvent> publisher,
      TransactionTemplate transactionTemplate,
      Settings settings,
      Clock clock,
      ProcessorMetrics metrics) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
    this.transactionTemplate = transactionTemplate;
    this.settings = settings;
    this.clock = clock;
    this.metrics = metrics;
    metrics.registerGauge(OLDEST_AGE_METRIC, Tags.empty(), oldestAgeSeconds);
    this.executor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              var thread = new Thread(runnable, "WatchboxAlertOutboxDrainer");
              thread.setDaemon(true);
              return thread;
            });
  }

  @PostConstruct
  public void start() {
    // execute, not submit: submit wraps the task in a Future nobody reads, which would swallow
    // even the Error that the guard below rethrows.
    executor.execute(this::guardedRunLoop);
  }

  /**
   * Death visibility for the single drain thread. {@code safeDrain} absorbs per-pass
   * RuntimeExceptions, so only an Error (or a bug in the loop itself) can get here — and a silent
   * exit would freeze the oldest-age gauge at its last healthy value while delivery on this
   * instance quietly stops. Log at ERROR, poison the gauge to a documented sentinel, and rethrow:
   * other instances' drainers still deliver (SKIP LOCKED), but this instance must not pretend.
   */
  private void guardedRunLoop() {
    try {
      runLoop();
    } catch (Throwable death) {
      log.error("Watchbox alert outbox drainer thread died; alerts no longer drain here", death);
      throw death;
    } finally {
      // Negative age is unreachable from real rows, so dashboards and alerts can key on it.
      oldestAgeSeconds.set(DRAINER_DEAD_AGE_SENTINEL);
    }
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  @Override
  public void requestDrain() {
    // A full queue means a drain is already pending: coalescing, not loss.
    var _ = nudge.offer(Boolean.TRUE);
  }

  private void runLoop() {
    log.info(
        "Watchbox alert outbox drainer started (batch {}, fallback poll {}, delivery window {})",
        settings.batchSize(),
        settings.fallbackPoll(),
        settings.deliveryWindow());
    while (!Thread.currentThread().isInterrupted()) {
      safeDrain();
      refreshOldestAge();
      try {
        nudge.poll(settings.fallbackPoll().toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void safeDrain() {
    try {
      drainAll();
    } catch (RuntimeException exception) {
      metrics.incrementCounter(DRAIN_FAILURES_METRIC, Tags.empty());
      // Single line at WARN: this fires every fallback interval for the whole outage, and a
      // stack trace per attempt makes the log unreadable exactly when an operator reads it.
      log.warn(
          "Watchbox alert outbox drain failed; claimed rows rolled back and will be retried: {}",
          exception.toString());
      log.debug("Watchbox alert outbox drain failure stack", exception);
    }
  }

  /** One claim/publish/delete transaction; a claim short of a full batch ends the drain. */
  private record DrainPass(int claimed, int published, int expired) {}

  /**
   * Drains until a claim comes back short of a full batch; returns rows removed from the outbox
   * (published plus expired).
   */
  int drainAll() {
    var totalPublished = 0;
    var totalExpired = 0;
    while (true) {
      // Do not cancel an active transaction: an interrupt observed after a committed pass only
      // prevents opening the next independent claim transaction.
      if (Thread.currentThread().isInterrupted()) {
        break;
      }
      var pass = transactionTemplate.execute(_ -> drainOnePass());
      if (pass == null) {
        throw new IllegalStateException("watchbox alert drain transaction returned no result");
      }
      totalPublished += pass.published();
      totalExpired += pass.expired();
      if (pass.published() > 0) {
        metrics.incrementCounter(PUBLISHED_METRIC, Tags.empty(), pass.published());
      }
      if (pass.expired() > 0) {
        metrics.incrementCounter(EXPIRED_METRIC, Tags.empty(), pass.expired());
      }
      if (pass.claimed() < settings.batchSize()) {
        break;
      }
    }
    if (totalPublished > 0) {
      log.info("Watchbox alert outbox drained {} rows", totalPublished);
    }
    if (totalExpired > 0) {
      log.warn(
          "Watchbox alert outbox dropped {} undelivered rows older than the {} delivery window",
          totalExpired,
          settings.deliveryWindow());
    }
    return totalPublished + totalExpired;
  }

  /**
   * Body of one claim/publish/delete transaction. Live-or-dropped partition: rows past the delivery
   * window are deleted without publishing, so an all-expired backlog drains while Redis is still
   * down. A fresh row's publish failure still rolls back the whole claim, expired deletions
   * included.
   */
  private DrainPass drainOnePass() {
    var claimed = outboxRepository.claimBatch(settings.batchSize());
    var expiredBefore = clock.instant().minus(settings.deliveryWindow());
    var expired = 0;
    for (var row : claimed) {
      if (row.event().effectiveEventTime().isBefore(expiredBefore)) {
        expired++;
      } else {
        publisher.publish(row.event());
      }
    }
    outboxRepository.deleteAll(claimed.stream().map(WatchboxAlertOutboxRow::id).toList());
    return new DrainPass(claimed.size(), claimed.size() - expired, expired);
  }

  private void refreshOldestAge() {
    try {
      var age =
          outboxRepository
              .oldestCreatedAt()
              .map(created -> Math.max(0L, Duration.between(created, clock.instant()).toSeconds()))
              .orElse(0L);
      oldestAgeSeconds.set(age);
    } catch (RuntimeException exception) {
      // Keep the last value: a failure here means the drain itself just failed and logged.
      log.debug("Watchbox alert outbox age refresh failed: {}", exception.getMessage());
    }
  }
}
