// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.gateway.socket.rtsp.KlvPacketFramer;
import com.opencop.api.connect.technical.gateway.socket.rtsp.NoKlvMetadataException;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractor;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorException;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorFactory;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability.Outcome;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability.Reason;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RtspKlvConnector implements SocketConnector, Runnable {

  private static final Logger log = LoggerFactory.getLogger(RtspKlvConnector.class);

  private final UUID sourceId;
  private final RtspConfig config;
  private final RtspKlvExtractorFactory extractorFactory;
  private final KlvPacketFramer framer;
  private final Duration firstMetadataTimeout;
  private final RtspKlvObservability observability;
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicReference<ConnectorPhase> phase =
      new AtomicReference<>(new ConnectorPhase.New());
  private final AtomicReference<RtspKlvExtractor> activeExtractor = new AtomicReference<>();

  public RtspKlvConnector(
      UUID sourceId,
      RtspConfig config,
      RtspKlvExtractorFactory extractorFactory,
      KlvPacketFramer framer,
      Duration firstMetadataTimeout,
      RtspKlvObservability observability) {
    this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
    this.config = Objects.requireNonNull(config, "config");
    this.extractorFactory = Objects.requireNonNull(extractorFactory, "extractorFactory");
    this.framer = Objects.requireNonNull(framer, "framer");
    this.firstMetadataTimeout = validateFirstMetadataTimeout(firstMetadataTimeout);
    this.observability = Objects.requireNonNull(observability, "observability");
  }

  @Override
  public void run() {
    var currentThread = Thread.currentThread();
    var current = phase.get();
    if (!(current instanceof ConnectorPhase.Connecting(Thread thread)) || thread != currentThread) {
      log.warn("RTSP KLV connection {} run() called in unexpected phase: {}", sourceId, current);
      return;
    }

    var sessionObservation = observability.startSession();
    var sessionOutcome = Outcome.ERROR;
    var sessionReason = Reason.FAILURE;
    Throwable sessionError = null;
    notifyState(ConnectorState.CONNECTING);
    RtspKlvExtractor extractor = null;
    try (var ignored = sessionObservation.makeCurrent()) {
      extractor = extractorFactory.create(config);
      activeExtractor.set(extractor);
      extractor.start();
      var session = sessionFor(extractor);
      if (!transitionToRunning(currentThread, session)) {
        closeExtractor(extractor);
        extractor = null;
        notifyState(ConnectorState.STOPPED);
        sessionOutcome = Outcome.STOPPED;
        sessionReason = Reason.NONE;
        return;
      }

      notifyState(ConnectorState.CONNECTED);
      var readLoopResult = readLoop(extractor);
      markDisconnectedOrStopped();
      sessionOutcome = outcomeForState(state(), readLoopResult.emittedKlvUnit());
      sessionReason =
          sessionOutcome == Outcome.INCOMPATIBLE ? readLoopResult.reason() : Reason.NONE;
    } catch (Throwable t) {
      if (phase.get() instanceof ConnectorPhase.Stopped) {
        notifyState(ConnectorState.STOPPED);
        sessionOutcome = Outcome.STOPPED;
        sessionReason = Reason.NONE;
        return;
      }
      sessionError = t;
      closeExtractor(extractor);
      extractor = null;
      markFailed(t);
    } finally {
      closeExtractor(extractor);
      sessionObservation.close(sessionOutcome, sessionReason, sessionError);
    }
  }

  private void closeExtractor(RtspKlvExtractor extractor) {
    if (extractor == null) {
      return;
    }
    extractor.close();
    activeExtractor.compareAndSet(extractor, null);
  }

  private boolean transitionToRunning(Thread currentThread, Session session) {
    while (true) {
      var before = phase.get();
      if (!(before instanceof ConnectorPhase.Connecting)) {
        return false;
      }
      if (phase.compareAndSet(before, new ConnectorPhase.Running(currentThread, session))) {
        return true;
      }
      Thread.onSpinWait();
    }
  }

  private ReadLoopResult readLoop(RtspKlvExtractor extractor) throws RtspKlvExtractorException {
    var firstKlvDeadline = System.nanoTime() + firstMetadataTimeout.toNanos();
    var firstKlvStarted = System.nanoTime();
    var emittedKlvUnit = false;

    try {
      while (phase.get() instanceof ConnectorPhase.Running) {
        if (Thread.currentThread().isInterrupted()) {
          return new ReadLoopResult(emittedKlvUnit, Reason.NONE);
        }

        var packet = extractor.readNextPacket(emittedKlvUnit ? Long.MAX_VALUE : firstKlvDeadline);
        if (packet == null) {
          if (!emittedKlvUnit) {
            var reason = Reason.STREAM_ENDED_BEFORE_KLV;
            observability.recordFirstUnit(
                Outcome.INCOMPATIBLE, reason, elapsedSince(firstKlvStarted));
            markIncompatible(
                new NoKlvMetadataException(
                    "RTSP source ended before producing complete KLV metadata"),
                reason);
            return new ReadLoopResult(false, reason);
          }
          return new ReadLoopResult(true, Reason.NONE);
        }

        var completeUnits = framer.appendPacket(packet);
        for (var unit : completeUnits) {
          if (!emittedKlvUnit) {
            observability.recordFirstUnit(
                Outcome.SUCCESS, Reason.NONE, elapsedSince(firstKlvStarted));
          }
          emittedKlvUnit = true;
          observability.recordKlvUnit(unit.length);
          notifyMessage(unit);
        }

        if (!emittedKlvUnit && System.nanoTime() >= firstKlvDeadline) {
          var reason = Reason.NO_KLV_BEFORE_DEADLINE;
          observability.recordFirstUnit(
              Outcome.INCOMPATIBLE, reason, elapsedSince(firstKlvStarted));
          markIncompatible(noKlvBeforeDeadlineException(null), reason);
          return new ReadLoopResult(false, reason);
        }
      }
    } catch (NoKlvMetadataException e) {
      var reason = Reason.NO_KLV_BEFORE_DEADLINE;
      if (!emittedKlvUnit) {
        observability.recordFirstUnit(Outcome.INCOMPATIBLE, reason, elapsedSince(firstKlvStarted));
      }
      markIncompatible(noKlvBeforeDeadlineException(e), reason);
      return new ReadLoopResult(emittedKlvUnit, reason);
    }
    return new ReadLoopResult(emittedKlvUnit, Reason.NONE);
  }

  private NoKlvMetadataException noKlvBeforeDeadlineException(Throwable cause) {
    var message =
        "RTSP source did not produce complete KLV metadata within "
            + firstMetadataTimeout.toMillis()
            + "ms";
    if (cause == null) {
      return new NoKlvMetadataException(message);
    }
    return new NoKlvMetadataException(message, cause);
  }

  private static Duration validateFirstMetadataTimeout(Duration timeout) {
    var value = Objects.requireNonNull(timeout, "firstMetadataTimeout");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("RTSP first metadata timeout must be positive");
    }
    return value;
  }

  private static Duration elapsedSince(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }

  private void markDisconnectedOrStopped() {
    var current = phase.get();
    if (current instanceof ConnectorPhase.Stopped) {
      notifyState(ConnectorState.STOPPED);
      return;
    }
    if (current instanceof ConnectorPhase.Running
        && phase.compareAndSet(current, new ConnectorPhase.Disconnected())) {
      notifyState(ConnectorState.DISCONNECTED);
    }
  }

  private void markIncompatible(NoKlvMetadataException reason, Reason observabilityReason) {
    while (true) {
      var current = phase.get();
      if (current instanceof ConnectorPhase.Stopped) {
        notifyState(ConnectorState.STOPPED);
        return;
      }
      if (phase.compareAndSet(current, new ConnectorPhase.Incompatible(reason))) {
        log.warn(
            "RTSP KLV connection {} marked INCOMPATIBLE reason={}",
            sourceId,
            observabilityReason.value());
        notifyError(reason);
        notifyState(ConnectorState.INCOMPATIBLE);
        return;
      }
      Thread.onSpinWait();
    }
  }

  private static Outcome outcomeForState(ConnectorState state, boolean emittedKlvUnit) {
    return switch (state) {
      case STOPPED -> Outcome.STOPPED;
      case DISCONNECTED -> emittedKlvUnit ? Outcome.SUCCESS : Outcome.DISCONNECTED;
      case INCOMPATIBLE -> Outcome.INCOMPATIBLE;
      case FAILED -> Outcome.ERROR;
      case CONNECTED -> Outcome.SUCCESS;
      case NEW, CONNECTING, UNKNOWN -> Outcome.ERROR;
    };
  }

  private record ReadLoopResult(boolean emittedKlvUnit, Reason reason) {}

  private void markFailed(Throwable throwable) {
    while (true) {
      var current = phase.get();
      if (current instanceof ConnectorPhase.Stopped) {
        notifyState(ConnectorState.STOPPED);
        return;
      }
      if (phase.compareAndSet(current, new ConnectorPhase.Failed(throwable))) {
        notifyError(throwable);
        notifyState(ConnectorState.FAILED);
        return;
      }
      Thread.onSpinWait();
    }
  }

  @Override
  public UUID id() {
    return sourceId;
  }

  @Override
  public ConnectorState state() {
    return switch (phase.get()) {
      case ConnectorPhase.New() -> ConnectorState.NEW;
      case ConnectorPhase.Connecting(_) -> ConnectorState.CONNECTING;
      case ConnectorPhase.Running(_, _) -> ConnectorState.CONNECTED;
      case ConnectorPhase.Stopped() -> ConnectorState.STOPPED;
      case ConnectorPhase.Disconnected() -> ConnectorState.DISCONNECTED;
      case ConnectorPhase.Incompatible(_) -> ConnectorState.INCOMPATIBLE;
      case ConnectorPhase.Failed(_) -> ConnectorState.FAILED;
    };
  }

  @Override
  public void start() {
    var thread = new Thread(this, "RtspKlvConnector-" + sourceId);
    thread.setDaemon(true);

    if (phase.get() instanceof ConnectorPhase.New current
        && phase.compareAndSet(current, new ConnectorPhase.Connecting(thread))) {
      thread.start();
    }
  }

  @Override
  public boolean stop() {
    while (true) {
      var current = phase.get();
      if (current instanceof ConnectorPhase.Running(Thread thread, Session session)) {
        if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
          session.close();
          thread.interrupt();
          return true;
        }
        Thread.onSpinWait();
      } else if (current instanceof ConnectorPhase.Connecting(Thread thread)) {
        if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
          var extractor = activeExtractor.get();
          if (extractor != null) {
            extractor.close();
          }
          thread.interrupt();
          return true;
        }
        Thread.onSpinWait();
      } else {
        return false;
      }
    }
  }

  @Override
  public void send(byte[] data) throws IOException {
    throw new UnsupportedOperationException("RTSP KLV connectors do not support send");
  }

  @Override
  public void addListener(ConnectorListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ConnectorListener listener) {
    listeners.remove(listener);
  }

  private Session sessionFor(RtspKlvExtractor extractor) {
    return new Session() {
      @Override
      public InputStream input() {
        throw new UnsupportedOperationException("RTSP KLV connector does not expose stream input");
      }

      @Override
      public OutputStream output() {
        throw new UnsupportedOperationException("RTSP KLV connector does not expose stream output");
      }

      @Override
      public void close() {
        extractor.close();
      }
    };
  }

  private void notifyState(ConnectorState state) {
    for (var listener : listeners) {
      try {
        listener.onStateChange(sourceId, state);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onStateChange", listener.getClass().getName(), e);
      }
    }
  }

  private void notifyMessage(byte[] message) {
    for (var listener : listeners) {
      try {
        listener.onMessage(sourceId, message);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onMessage", listener.getClass().getName(), e);
      }
    }
  }

  private void notifyError(Throwable throwable) {
    for (var listener : listeners) {
      try {
        listener.onError(sourceId, throwable);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onError", listener.getClass().getName(), e);
      }
    }
  }
}
