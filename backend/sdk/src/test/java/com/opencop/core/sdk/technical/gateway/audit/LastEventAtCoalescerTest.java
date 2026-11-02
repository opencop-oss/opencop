// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.gateway.audit.LastEventAtWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Drives the coalescer with a hand-controlled clock and no scheduler, so every assertion is about
 * merge and retry semantics rather than about timing.
 */
class LastEventAtCoalescerTest {

  private static final UUID RESOURCE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID OTHER_RESOURCE =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final Instant START = Instant.parse("2026-09-04T12:00:00Z");
  private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(30);

  private final AtomicReference<Instant> now = new AtomicReference<>(START);
  private final List<Write> writes = new ArrayList<>();

  private record Write(UUID resourceId, Instant observedAt) {}

  /** A clock the test advances by hand, so no assertion depends on wall-clock time. */
  private Clock fixedClock() {
    return new Clock() {
      @Override
      public ZoneOffset getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(java.time.ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now.get();
      }
    };
  }

  private LastEventAtCoalescer coalescerWriting(LastEventAtWriter writer) {
    // Null scheduler: flushes are driven explicitly, so no background thread can race assertions.
    return new LastEventAtCoalescer("test", writer, FLUSH_INTERVAL, fixedClock(), null, () -> {});
  }

  private LastEventAtCoalescer recordingCoalescer() {
    return coalescerWriting((id, at) -> writes.add(new Write(id, at)));
  }

  @Test
  void writesTheObservationOnTheFirstFlush() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    assertEquals(List.of(new Write(RESOURCE, START)), writes);
  }

  @Test
  void collapsesRepeatedObservationsIntoOneWriteKeepingTheLatest() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.record(RESOURCE, START.plusSeconds(5));
    coalescer.record(RESOURCE, START.plusSeconds(12));
    coalescer.flushDue();

    assertEquals(
        List.of(new Write(RESOURCE, START.plusSeconds(12))),
        writes,
        "three observations in one interval must cost a single write carrying the newest instant");
  }

  @Test
  void keepsTheLatestWhenObservationsArriveOutOfOrder() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START.plusSeconds(30));
    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    assertEquals(List.of(new Write(RESOURCE, START.plusSeconds(30))), writes);
  }

  @Test
  void skipsAResourceWhoseIntervalHasNotElapsed() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    now.set(START.plusSeconds(5));
    coalescer.record(RESOURCE, START.plusSeconds(5));
    coalescer.flushDue();

    assertEquals(1, writes.size(), "a second flush inside the interval must not write again");
  }

  @Test
  void writesAgainOnceTheIntervalHasElapsed() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    now.set(START.plus(FLUSH_INTERVAL));
    coalescer.record(RESOURCE, START.plusSeconds(20));
    coalescer.flushDue();

    assertEquals(
        List.of(new Write(RESOURCE, START), new Write(RESOURCE, START.plusSeconds(20))), writes);
  }

  @Test
  void flushAllIgnoresTheIntervalSoShutdownLosesNothing() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    now.set(START.plusSeconds(1));
    coalescer.record(RESOURCE, START.plusSeconds(1));
    coalescer.flushAll();

    assertEquals(2, writes.size(), "a forced flush must write even inside the interval");
  }

  @Test
  void doesNotWriteAgainWhenNothingNewWasObserved() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();

    now.set(START.plus(FLUSH_INTERVAL));
    coalescer.flushDue();

    assertEquals(1, writes.size(), "an idle resource must not be rewritten on later intervals");
  }

  @Test
  void tracksResourcesIndependently() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.record(OTHER_RESOURCE, START.plusSeconds(3));
    coalescer.flushDue();

    assertEquals(2, writes.size());
    assertTrue(writes.contains(new Write(RESOURCE, START)));
    assertTrue(writes.contains(new Write(OTHER_RESOURCE, START.plusSeconds(3))));
  }

  @Test
  void retriesAfterATransientWriteFailure() {
    var failNext = new AtomicReference<>(true);
    var coalescer =
        coalescerWriting(
            (id, at) -> {
              if (failNext.getAndSet(false)) {
                throw new IllegalStateException("connection reset");
              }
              writes.add(new Write(id, at));
            });

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();
    assertTrue(writes.isEmpty(), "the failing write must not be recorded");

    // No clock advance: a failed flush leaves the resource due, so the next tick retries at once.
    coalescer.flushDue();
    assertEquals(
        List.of(new Write(RESOURCE, START)),
        writes,
        "a transient failure must keep the observation pending for the next interval");
  }

  @Test
  void dropsAnObservationWhoseResourceIsGone() {
    var attempts = new AtomicReference<>(0);
    var coalescer =
        coalescerWriting(
            (_, _) -> {
              attempts.updateAndGet(count -> count + 1);
              throw new DataIntegrityViolationException("data_mapper row no longer exists");
            });

    coalescer.record(RESOURCE, START);
    coalescer.flushDue();
    coalescer.flushDue();
    coalescer.flushAll();

    assertEquals(
        1,
        attempts.get(),
        "a deleted resource must be abandoned after one attempt rather than retried forever");
  }

  @Test
  void keepsAnObservationThatArrivesWhileTheFlushIsCleaningUp() {
    // The window this guards: flush() has already written and CAS'd the pending slot to null, and
    // is about to drop the map entry, when a fresh record() lands on the very same reference.
    // Without the double-check in removeIfStillEmpty, that observation would be swept away with
    // the entry and silently lost.
    var newer = START.plusSeconds(45);
    var injected = new AtomicBoolean(false);
    var coalescerRef = new AtomicReference<LastEventAtCoalescer>();
    var coalescer =
        new LastEventAtCoalescer(
            "test",
            (id, at) -> writes.add(new Write(id, at)),
            FLUSH_INTERVAL,
            fixedClock(),
            null,
            () -> {
              if (injected.compareAndSet(false, true)) {
                coalescerRef.get().record(RESOURCE, newer);
              }
            });
    coalescerRef.set(coalescer);
    coalescer.record(RESOURCE, START);

    coalescer.flushAll();
    coalescer.flushAll();

    assertEquals(
        List.of(new Write(RESOURCE, START), new Write(RESOURCE, newer)),
        writes,
        "an observation racing the pending-entry cleanup must survive to the next flush");
  }

  @Test
  void closeFlushesWhatIsStillPending() {
    var coalescer = recordingCoalescer();

    coalescer.record(RESOURCE, START);
    coalescer.close();

    assertEquals(List.of(new Write(RESOURCE, START)), writes);
  }
}
