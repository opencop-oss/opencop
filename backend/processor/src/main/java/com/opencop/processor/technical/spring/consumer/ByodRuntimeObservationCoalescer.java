// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.helper.InstanceIdentity;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.processor.technical.spring.config.byod.ByodRuntimeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ByodRuntimeObservationCoalescer {

  private static final Logger log = LoggerFactory.getLogger(ByodRuntimeObservationCoalescer.class);
  private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(5);
  private static final Duration DEFAULT_STATUS_TTL = Duration.ofMinutes(5);

  private final SourceRuntimeObservationWriter writer;
  private final Duration flushInterval;
  private final Duration statusTtl;
  private final String instanceId;
  private final Clock clock;
  private final ScheduledExecutorService scheduler;
  private final Runnable beforePendingCleanup;
  private final AtomicBoolean started = new AtomicBoolean(false);
  // Latest successful message timestamp per source waiting to be flushed as lastMessageAt.
  private final ConcurrentHashMap<UUID, AtomicReference<Observation>> pending =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, Instant> lastSuccessfulFlushAt = new ConcurrentHashMap<>();

  @Autowired
  public ByodRuntimeObservationCoalescer(
      SourceRuntimeObservationWriter writer, ByodRuntimeProperties properties) {
    this(
        writer,
        properties,
        InstanceIdentity.resolveInstanceId("processor"),
        Clock.systemUTC(),
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var thread = new Thread(r, "byod-runtime-observation-flush");
              thread.setDaemon(true);
              return thread;
            }),
        () -> {});
  }

  // Package-private for deterministic tests; production uses the @Autowired constructor.
  ByodRuntimeObservationCoalescer(
      SourceRuntimeObservationWriter writer,
      ByodRuntimeProperties properties,
      String instanceId,
      Clock clock,
      ScheduledExecutorService scheduler,
      Runnable beforePendingCleanup) {
    this.writer = Objects.requireNonNull(writer, "writer");
    this.flushInterval = positiveOrDefault(properties.flushInterval(), DEFAULT_FLUSH_INTERVAL);
    this.statusTtl = positiveOrDefault(properties.statusTtl(), DEFAULT_STATUS_TTL);
    this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.scheduler = scheduler;
    this.beforePendingCleanup =
        Objects.requireNonNull(beforePendingCleanup, "beforePendingCleanup");
  }

  @PostConstruct
  void start() {
    if (scheduler == null || !started.compareAndSet(false, true)) {
      return;
    }
    scheduler.scheduleAtFixedRate(
        this::flushDueSafely,
        flushInterval.toMillis(),
        flushInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  void stop() {
    flushAll();
    if (scheduler != null) {
      scheduler.shutdown();
    }
  }

  /**
   * Notes a successfully handled message.
   *
   * <p>{@code messageToken} identifies the message itself, so the durable activity derived from
   * this observation can tell a genuinely new message from a heartbeat re-reporting the same one.
   * Latest-wins keeps the token belonging to whichever observation won, since the two must describe
   * the same message.
   */
  public void recordSuccess(UUID sourceId, Instant messageAt, String messageToken) {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(messageAt, "messageAt");
    var observation = new Observation(messageAt, messageToken);
    pending
        .computeIfAbsent(sourceId, _ -> new AtomicReference<>())
        .updateAndGet(
            current -> current == null || messageAt.isAfter(current.at()) ? observation : current);
  }

  /**
   * Without a message identity: the observation still refreshes liveness, but promotes no durable
   * activity — the upsert preserves whatever token the row already carried, so the activity
   * trigger's new-or-changed test does not fire. The production BYOD path always supplies one.
   */
  public void recordSuccess(UUID sourceId, Instant messageAt) {
    recordSuccess(sourceId, messageAt, null);
  }

  /** The newest handled message for a source: when it arrived, and which message it was. */
  private record Observation(Instant at, String token) {}

  public void flush(UUID sourceId) {
    flush(sourceId, true);
  }

  public void flushAll() {
    for (var sourceId : pending.keySet()) {
      flush(sourceId, true);
    }
  }

  void flushDue() {
    for (var sourceId : pending.keySet()) {
      flush(sourceId, false);
    }
  }

  private void flushDueSafely() {
    try {
      flushDue();
    } catch (Throwable t) {
      log.warn("BYOD runtime observation flush failed unexpectedly", t);
    }
  }

  private void flush(UUID sourceId, boolean force) {
    var latestRef = pending.get(sourceId);
    if (latestRef == null) {
      return;
    }
    var latest = latestRef.get();
    if (latest == null) {
      removeIfStillEmpty(sourceId, latestRef);
      return;
    }
    if (!force && !isFlushDue(sourceId)) {
      return;
    }

    try {
      writer.writeRuntime(
          new RuntimeStatus(
              sourceId, ConnectorState.CONNECTED, latest.at(), instanceId, null, latest.token()),
          statusTtl);
      lastSuccessfulFlushAt.put(sourceId, clock.instant());
      if (latestRef.compareAndSet(latest, null)) {
        removeIfStillEmpty(sourceId, latestRef);
      }
    } catch (DataIntegrityViolationException e) {
      pending.remove(sourceId, latestRef);
      lastSuccessfulFlushAt.remove(sourceId);
      log.debug(
          "Dropping BYOD runtime observation for {} after terminal Postgres integrity failure: {}",
          sourceId,
          e.getMessage());
    } catch (Exception e) {
      log.warn("Postgres write failed while flushing BYOD runtime observations");
      log.debug("Postgres write failed for BYOD runtime {}: {}", sourceId, e.getMessage(), e);
    }
  }

  private void removeIfStillEmpty(UUID sourceId, AtomicReference<Observation> latestRef) {
    beforePendingCleanup.run();
    pending.computeIfPresent(
        sourceId,
        (_, currentRef) -> currentRef == latestRef && currentRef.get() == null ? null : currentRef);
  }

  private boolean isFlushDue(UUID sourceId) {
    var lastFlush = lastSuccessfulFlushAt.get(sourceId);
    return lastFlush == null || !clock.instant().minus(flushInterval).isBefore(lastFlush);
  }

  private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
    return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
  }
}
