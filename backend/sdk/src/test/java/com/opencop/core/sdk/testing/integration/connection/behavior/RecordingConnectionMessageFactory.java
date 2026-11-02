// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Thread-safe message factory that records observable invocation details for consumer tests. */
public final class RecordingConnectionMessageFactory implements ConnectionMessageFactory {

  private final String eventName;
  private final byte[] payload;
  private final LifecycleEventRecorder events;
  private final Exception configuredFailure;
  private final CountDownLatch invocationSignal;
  private final CountDownLatch completionGate;
  private final List<Invocation> invocations = new CopyOnWriteArrayList<>();

  public RecordingConnectionMessageFactory(
      String eventName, byte[] payload, LifecycleEventRecorder events) {
    this(eventName, payload, events, null, new CountDownLatch(0), new CountDownLatch(0));
  }

  private RecordingConnectionMessageFactory(
      String eventName,
      byte[] payload,
      LifecycleEventRecorder events,
      Exception configuredFailure,
      CountDownLatch invocationSignal,
      CountDownLatch completionGate) {
    this.eventName = Objects.requireNonNull(eventName, "eventName");
    this.payload = Objects.requireNonNull(payload, "payload").clone();
    this.events = Objects.requireNonNull(events, "events");
    this.configuredFailure = configuredFailure;
    this.invocationSignal = Objects.requireNonNull(invocationSignal, "invocationSignal");
    this.completionGate = Objects.requireNonNull(completionGate, "completionGate");
  }

  /**
   * Returns a recorder that throws the supplied expected failure after capturing each invocation.
   */
  public static RecordingConnectionMessageFactory failing(
      String eventName, byte[] payload, LifecycleEventRecorder events, Exception failure) {
    return new RecordingConnectionMessageFactory(
        eventName,
        payload,
        events,
        Objects.requireNonNull(failure, "failure"),
        new CountDownLatch(0),
        new CountDownLatch(0));
  }

  /**
   * Returns a recorder that signals invocation and waits until the supplied completion gate opens.
   */
  public static RecordingConnectionMessageFactory gated(
      String eventName,
      byte[] payload,
      LifecycleEventRecorder events,
      CountDownLatch invocationSignal,
      CountDownLatch completionGate) {
    return new RecordingConnectionMessageFactory(
        eventName, payload, events, null, invocationSignal, completionGate);
  }

  @Override
  public byte[] create(UUID sourceId, Instant executionTime) throws Exception {
    long order = events.record(eventName);
    invocations.add(
        new Invocation(
            sourceId, executionTime, order, payload, Optional.ofNullable(configuredFailure)));
    invocationSignal.countDown();
    completionGate.await();
    if (configuredFailure != null) {
      throw configuredFailure;
    }
    return payload.clone();
  }

  /** Returns an immutable snapshot of recorded invocations. */
  public List<Invocation> invocations() {
    return invocations.stream().sorted(Comparator.comparingLong(Invocation::order)).toList();
  }

  /** Captured inputs, ordering, output payload, and configured failure for one invocation. */
  public record Invocation(
      UUID sourceId,
      Instant executionTime,
      long order,
      byte[] payload,
      Optional<Exception> failure) {

    public Invocation {
      Objects.requireNonNull(sourceId, "sourceId");
      Objects.requireNonNull(executionTime, "executionTime");
      if (order < 0) {
        throw new IllegalArgumentException("order must not be negative");
      }
      payload = Objects.requireNonNull(payload, "payload").clone();
      failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public byte[] payload() {
      return payload.clone();
    }
  }
}
