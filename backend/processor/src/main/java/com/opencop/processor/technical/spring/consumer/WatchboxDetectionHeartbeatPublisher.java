// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.helper.InstanceIdentity;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.processor.technical.spring.config.broker.WatchboxRetainedRetryTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.stereotype.Component;

/**
 * Publishes the watchbox-detection liveness heartbeat from listener evidence, so Core's status
 * rollup can distinguish "no bad news" from "nobody is detecting anything".
 *
 * <p>The heartbeat row is refreshed only while the poll loop proves it is turning: a successfully
 * processed batch, or a {@link ListenerContainerIdleEvent} from the watchbox containers (the
 * no-traffic case; the watchbox listener is the only one configured to emit them). Evidence is
 * instance-wide, not per consumer thread: any child's batch or idle event refreshes the shared
 * timestamp, so the row proves some child is turning — a single wedged child at concurrency &gt; 1
 * is masked until its partitions rebalance away at max.poll.interval. The known asymmetric case, a
 * child wedged in retained retry-in-place, is covered by carrying the {@link
 * WatchboxRetainedRetryTracker} aggregate on every row write: the sibling's evidence keeps flushes
 * flowing, so the wedge facts reach the row within a flush interval or two, and the read side
 * degrades status from them. Only when every child stops does the row age out and status turn amber
 * within the TTL. Absence of evidence must never read as healthy.
 *
 * <p>Writes are coalesced on a fixed interval rather than per record, following {@link
 * ByodRuntimeObservationCoalescer}'s discipline, and the row carries the container's assigned
 * partition count: an idle event proves polling but not assignment, and a consumer with zero
 * assignment polls happily forever.
 *
 * <p>The stop marker is this process's final heartbeat write, enforced with a lock rather than
 * relying on scheduler shutdown timing: the repository's upsert deliberately revives stopped rows
 * (Docker instance ids are container-stable, so a restart must be able to reclaim its row), which
 * means a straggler flush landing after {@link #stop()} would silently resurrect a row Core is
 * supposed to read as dead.
 */
@Component
public class WatchboxDetectionHeartbeatPublisher {

  // Same layer as Connect's instance heartbeat (its worker-alive signal, not its per-source
  // observations): 60s TTL with 10s refresh gives a 6x margin before a GC pause or transient PG
  // slowness makes a healthy instance appear dead.
  static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
  static final Duration HEARTBEAT_TTL = Duration.ofSeconds(60);

  private static final Logger log =
      LoggerFactory.getLogger(WatchboxDetectionHeartbeatPublisher.class);

  private final WatchboxDetectionHeartbeatWriter writer;
  private final Supplier<KafkaListenerEndpointRegistry> registry;
  private final WatchboxRetainedRetryTracker retainedRetryTracker;
  private final String instanceId;
  private final Clock clock;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicLong sequence = new AtomicLong();
  private final AtomicReference<Instant> aliveSince = new AtomicReference<>();
  private final AtomicReference<Instant> lastEvidenceAt = new AtomicReference<>();
  private final AtomicReference<Instant> lastEvaluatedAt = new AtomicReference<>();
  private volatile Instant lastFlushedEvidenceAt;

  private final Object writeLock = new Object();

  // Guarded by writeLock. Once set, no heartbeat may be written: markStopped is final.
  private boolean stopped;

  @Autowired
  public WatchboxDetectionHeartbeatPublisher(
      WatchboxDetectionHeartbeatWriter writer,
      ObjectProvider<KafkaListenerEndpointRegistry> registry,
      WatchboxRetainedRetryTracker retainedRetryTracker) {
    this(
        writer,
        registry::getIfAvailable,
        retainedRetryTracker,
        InstanceIdentity.resolveInstanceId("processor"),
        Clock.systemUTC(),
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var thread = new Thread(r, "watchbox-detection-heartbeat");
              thread.setDaemon(true);
              return thread;
            }));
  }

  // Package-private for deterministic tests; production uses the @Autowired constructor.
  WatchboxDetectionHeartbeatPublisher(
      WatchboxDetectionHeartbeatWriter writer,
      Supplier<KafkaListenerEndpointRegistry> registry,
      WatchboxRetainedRetryTracker retainedRetryTracker,
      String instanceId,
      Clock clock,
      ScheduledExecutorService scheduler) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.retainedRetryTracker =
        Objects.requireNonNull(retainedRetryTracker, "retainedRetryTracker");
    this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.scheduler = scheduler;
  }

  @PostConstruct
  void start() {
    if (scheduler == null || !started.compareAndSet(false, true)) {
      return;
    }
    scheduler.scheduleAtFixedRate(
        this::flushSafely,
        HEARTBEAT_INTERVAL.toMillis(),
        HEARTBEAT_INTERVAL.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  void stop() {
    if (scheduler != null) {
      scheduler.shutdown();
    }
    // Taking the lock serializes against any in-flight flush; setting stopped under it makes the
    // stop marker the final write even if the scheduler thread is mid-run right now.
    synchronized (writeLock) {
      stopped = true;
      try {
        // Expire the row immediately so status turns amber without waiting out the TTL.
        writer.markStopped(instanceId);
      } catch (Exception e) {
        log.warn("Failed to mark watchbox detection heartbeat stopped: {}", e.getMessage());
      }
    }
  }

  /** Called by the listener after a successfully processed batch: the poll loop is proved live. */
  void recordBatchProcessed() {
    var now = clock.instant();
    aliveSince.compareAndSet(null, now);
    lastEvaluatedAt.set(now);
    lastEvidenceAt.set(now);
  }

  /** An idle watchbox container proves the poll loop is turning even with no traffic. */
  @EventListener
  public void onListenerIdle(ListenerContainerIdleEvent event) {
    var listenerId = event.getListenerId();
    if (listenerId == null || !listenerId.startsWith(WatchboxConsumer.LISTENER_ID)) {
      return;
    }
    var now = clock.instant();
    aliveSince.compareAndSet(null, now);
    lastEvidenceAt.set(now);
  }

  void flush() {
    // Read the tracker aggregate on every tick, before the evidence gate: the read refreshes the
    // retained-retry gauges, so a total wedge - every child stuck, no evidence, row ageing toward
    // NOT_RUNNING - still shows live gauge values while the row write below stays evidence-gated.
    var retainedRetry = retainedRetryTracker.aggregate();
    var evidence = lastEvidenceAt.get();
    if (evidence == null || evidence.equals(lastFlushedEvidenceAt)) {
      // No fresh proof since the last write: let the row age toward its TTL rather than
      // refreshing it on a timer. A timer-refreshed heartbeat would report a wedged consumer
      // healthy, which is the exact inference this heartbeat exists to eliminate.
      return;
    }
    synchronized (writeLock) {
      if (stopped) {
        return;
      }
      try {
        writer.writeHeartbeat(
            new WatchboxDetectionHeartbeat(
                instanceId,
                aliveSince.get(),
                lastEvaluatedAt.get(),
                assignedPartitions(),
                sequence.incrementAndGet(),
                retainedRetry),
            HEARTBEAT_TTL);
        lastFlushedEvidenceAt = evidence;
      } catch (DataIntegrityViolationException e) {
        // Terminal integrity failure must not spin; drop this observation and wait for new
        // evidence.
        lastFlushedEvidenceAt = evidence;
        log.debug(
            "Dropping watchbox detection heartbeat after terminal Postgres integrity failure: {}",
            e.getMessage());
      } catch (Exception e) {
        log.warn("Postgres write failed while flushing the watchbox detection heartbeat");
        log.debug("Watchbox detection heartbeat write failed: {}", e.getMessage(), e);
      }
    }
  }

  private void flushSafely() {
    try {
      flush();
    } catch (Throwable t) {
      log.warn("Watchbox detection heartbeat flush failed unexpectedly", t);
    }
  }

  private int assignedPartitions() {
    var endpointRegistry = registry.get();
    var container =
        endpointRegistry == null
            ? null
            : endpointRegistry.getListenerContainer(WatchboxConsumer.LISTENER_ID);
    var assigned = container == null ? null : container.getAssignedPartitions();
    return assigned == null ? 0 : assigned.size();
  }
}
