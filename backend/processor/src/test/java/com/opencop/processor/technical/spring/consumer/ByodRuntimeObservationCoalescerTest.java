// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.processor.technical.spring.config.byod.ByodRuntimeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class ByodRuntimeObservationCoalescerTest {

  private static final UUID SOURCE_ID = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
  private static final String INSTANCE_ID = "processor-test";
  private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(5);
  private static final Duration STATUS_TTL = Duration.ofMinutes(5);
  private static final Instant NOW = Instant.parse("2026-05-02T12:00:00Z");

  private SourceRuntimeObservationWriter writer;
  private MutableClock clock;
  private ByodRuntimeObservationCoalescer coalescer;

  @BeforeEach
  void setUp() {
    writer = mock(SourceRuntimeObservationWriter.class);
    clock = new MutableClock(NOW);
    coalescer = newCoalescer();
  }

  @Test
  void flushAllWritesLatestTimestampForSource() {
    var first = NOW.minusSeconds(10);
    var latest = NOW;
    coalescer.recordSuccess(SOURCE_ID, first);
    coalescer.recordSuccess(SOURCE_ID, latest);

    coalescer.flushAll();

    var status = captureRuntime();
    assertThat(status.id()).isEqualTo(SOURCE_ID);
    assertThat(status.state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(status.lastMessageAt()).isEqualTo(latest);
    assertThat(status.instanceId()).isEqualTo(INSTANCE_ID);
  }

  @Test
  void flushDueWritesAtMostOncePerInterval() {
    coalescer.recordSuccess(SOURCE_ID, NOW);
    coalescer.flushDue();
    coalescer.recordSuccess(SOURCE_ID, NOW.plusSeconds(1));
    coalescer.flushDue();
    clock.advance(FLUSH_INTERVAL);
    coalescer.flushDue();

    verify(writer, times(2)).writeRuntime(org.mockito.ArgumentMatchers.any(), eq(STATUS_TTL));
  }

  @Test
  void forcedFlushWritesBeforeIntervalBoundary() {
    coalescer.recordSuccess(SOURCE_ID, NOW);
    coalescer.flushDue();
    coalescer.recordSuccess(SOURCE_ID, NOW.plusSeconds(1));

    coalescer.flush(SOURCE_ID);

    verify(writer, times(2)).writeRuntime(org.mockito.ArgumentMatchers.any(), eq(STATUS_TTL));
  }

  @Test
  void writeFailureKeepsPendingTimestampForRetry() {
    doThrow(new IllegalStateException("postgres unavailable"))
        .doNothing()
        .when(writer)
        .writeRuntime(org.mockito.ArgumentMatchers.any(RuntimeStatus.class), eq(STATUS_TTL));
    coalescer.recordSuccess(SOURCE_ID, NOW);

    coalescer.flushAll();
    coalescer.flushAll();

    verify(writer, times(2)).writeRuntime(org.mockito.ArgumentMatchers.any(), eq(STATUS_TTL));
  }

  @Test
  void integrityFailureDropsPendingTimestamp() {
    doThrow(new DataIntegrityViolationException("source deleted"))
        .when(writer)
        .writeRuntime(org.mockito.ArgumentMatchers.any(RuntimeStatus.class), eq(STATUS_TTL));
    coalescer.recordSuccess(SOURCE_ID, NOW);

    coalescer.flushAll();
    coalescer.flushAll();

    verify(writer).writeRuntime(org.mockito.ArgumentMatchers.any(), eq(STATUS_TTL));
  }

  @Test
  void integrityFailureClearsFlushThrottleForFutureRecords() {
    doNothing()
        .doThrow(new DataIntegrityViolationException("source deleted"))
        .doNothing()
        .when(writer)
        .writeRuntime(org.mockito.ArgumentMatchers.any(RuntimeStatus.class), eq(STATUS_TTL));
    coalescer.recordSuccess(SOURCE_ID, NOW);

    coalescer.flushAll();
    coalescer.recordSuccess(SOURCE_ID, NOW.plusSeconds(1));
    coalescer.flushAll();
    coalescer.recordSuccess(SOURCE_ID, NOW.plusSeconds(2));
    coalescer.flushDue();

    verify(writer, times(3)).writeRuntime(org.mockito.ArgumentMatchers.any(), eq(STATUS_TTL));
  }

  @Test
  void flush_whenRecordSuccessArrivesDuringCleanupDoesNotDropNewerTimestamp() {
    var first = NOW.minusSeconds(10);
    var newer = NOW.plusSeconds(1);
    var recordedDuringCleanup = new AtomicBoolean(false);
    var coalescerRef = new AtomicReference<ByodRuntimeObservationCoalescer>();
    coalescer =
        newCoalescer(
            () -> {
              if (recordedDuringCleanup.compareAndSet(false, true)) {
                coalescerRef.get().recordSuccess(SOURCE_ID, newer);
              }
            });
    coalescerRef.set(coalescer);
    coalescer.recordSuccess(SOURCE_ID, first);

    coalescer.flushAll();
    coalescer.flushAll();

    var captor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(writer, times(2)).writeRuntime(captor.capture(), eq(STATUS_TTL));
    assertThat(captor.getAllValues().get(0).lastMessageAt()).isEqualTo(first);
    assertThat(captor.getAllValues().get(1).lastMessageAt()).isEqualTo(newer);
  }

  private RuntimeStatus captureRuntime() {
    var captor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(writer).writeRuntime(captor.capture(), eq(STATUS_TTL));
    return captor.getValue();
  }

  private ByodRuntimeObservationCoalescer newCoalescer() {
    return newCoalescer(() -> {});
  }

  private ByodRuntimeObservationCoalescer newCoalescer(Runnable beforePendingCleanup) {
    return new ByodRuntimeObservationCoalescer(
        writer, runtimeProperties(), INSTANCE_ID, clock, null, beforePendingCleanup);
  }

  private static ByodRuntimeProperties runtimeProperties() {
    return new ByodRuntimeProperties(FLUSH_INTERVAL, STATUS_TTL);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
