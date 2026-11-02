// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.api.connect.component.support.RecordingConnectorListener;
import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.api.connect.technical.gateway.socket.rtsp.KlvPacketFramer;
import com.opencop.api.connect.technical.gateway.socket.rtsp.NoKlvMetadataException;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractor;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorException;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorFactory;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RtspKlvConnectorTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final String SESSIONS_TOTAL = "opencop_connect_rtsp_klv_sessions_total";
  private static final String FIRST_UNIT_DURATION =
      "opencop_connect_rtsp_klv_first_unit_duration_seconds";
  private static final String UNIT_BYTES = "opencop_connect_rtsp_klv_unit_bytes";
  private static final String ACTIVE_STREAMS = "opencop_connect_rtsp_klv_active_streams";

  @Test
  void emitsOneCompleteKlvUnitFromExtractorPacket() throws Exception {
    var unit = klv(3, 0x11);
    var harness = Harness.withPackets(unit);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(unit);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.DISCONNECTED);
    assertThat(harness.meterRegistry.get("opencop_connect_rtsp_klv_units_total").counter().count())
        .isEqualTo(1.0d);
    assertThat(harness.meterRegistry.get(UNIT_BYTES).summary().totalAmount())
        .isEqualTo((double) unit.length);
    assertThat(
            harness
                .meterRegistry
                .get(FIRST_UNIT_DURATION)
                .tags("outcome", "success", "reason", "NONE")
                .timer()
                .count())
        .isEqualTo(1L);
    assertSessionEventually(harness, "success", "NONE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void emitsMultipleKlvUnitsFromOneExtractorPacket() throws Exception {
    var first = klv(1, 0x11);
    var second = klv(2, 0x22);
    var harness = Harness.withPackets(concat(first, second));

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(first);
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(second);
  }

  @Test
  void buffersPartialKlvUnitsAcrossExtractorPackets() throws Exception {
    var unit = klv(5, 0x33);
    var harness = Harness.withPackets(slice(unit, 0, 10), slice(unit, 10, unit.length));

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(unit);
  }

  @Test
  void ignoresNonKlvPackets() throws Exception {
    var unit = klv(2, 0x44);
    var harness = Harness.withPackets(new byte[] {0x01, 0x02, 0x03}, unit);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(unit);
  }

  @Test
  void emitsCompleteKlvUnitWithoutDataStreamPrecheck() throws Exception {
    var unit = klv(3, 0x55);
    var harness = Harness.withPackets(unit);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.extractor.awaitReadStarted()).isTrue();
    assertThat(harness.listener.awaitMessage(TIMEOUT)).isEqualTo(unit);
  }

  @Test
  void transitionsToIncompatibleWhenNoCompleteKlvArrivesBeforeMetadataTimeout() throws Exception {
    var harness =
        Harness.withConfigAndExtractor(
            new RtspConfig("rtsp://localhost:8554/klv", null),
            Duration.ofMillis(25),
            new FakeExtractor(new ArrayDeque<>(List.of(new byte[] {0x01})), true));

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitError(TIMEOUT)).isInstanceOf(NoKlvMetadataException.class);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertSessionEventually(harness, "incompatible", "NO_KLV_BEFORE_DEADLINE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void transitionsToIncompatibleWhenExtractorHitsFirstKlvDeadlineInternally() throws Exception {
    var harness =
        Harness.withConfigAndExtractor(
            new RtspConfig("rtsp://localhost:8554/klv", null),
            Duration.ofMillis(25),
            FakeExtractor.filteringUntilDeadline());

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.extractor.awaitReadStarted()).isTrue();
    assertThat(harness.listener.awaitError(TIMEOUT)).isInstanceOf(NoKlvMetadataException.class);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertThat(
            harness
                .meterRegistry
                .get(FIRST_UNIT_DURATION)
                .tags("outcome", "incompatible", "reason", "NO_KLV_BEFORE_DEADLINE")
                .timer()
                .count())
        .isEqualTo(1L);
    assertSessionEventually(harness, "incompatible", "NO_KLV_BEFORE_DEADLINE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void transitionsToIncompatibleWhenStreamEndsBeforeFirstKlv() throws Exception {
    var harness = Harness.withPackets(new byte[] {0x01, 0x02, 0x03});

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(harness.listener.awaitError(TIMEOUT)).isInstanceOf(NoKlvMetadataException.class);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertThat(harness.connector.state()).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertThat(
            harness
                .meterRegistry
                .get(FIRST_UNIT_DURATION)
                .tags("outcome", "incompatible", "reason", "STREAM_ENDED_BEFORE_KLV")
                .timer()
                .count())
        .isEqualTo(1L);
    assertSessionEventually(harness, "incompatible", "STREAM_ENDED_BEFORE_KLV");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void stopDuringActiveReadClosesExtractorAndReportsStopped() throws Exception {
    var extractor = FakeExtractor.blocking();
    var harness = Harness.withExtractor(extractor);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTED);
    assertThat(extractor.awaitReadStarted()).isTrue();

    assertThat(harness.connector.stop()).isTrue();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.STOPPED);
    assertThat(extractor.closed()).isTrue();
    assertThat(harness.listener.pendingErrorCount()).isZero();
    assertSessionEventually(harness, "stopped", "NONE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void stopDuringConnectClosesExtractorAndReportsStopped() throws Exception {
    var extractor = FakeExtractor.blockingStart();
    var harness = Harness.withExtractor(extractor);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(extractor.awaitStartStarted()).isTrue();

    assertThat(harness.connector.stop()).isTrue();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.STOPPED);
    assertThat(extractor.closed()).isTrue();
    assertThat(harness.listener.pendingErrorCount()).isZero();
    assertSessionEventually(harness, "stopped", "NONE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void factoryFailureReportsFailedSessionWithoutFirstUnitMetric() throws Exception {
    var failure = new IllegalStateException("factory failed");
    var harness = Harness.withFactory(FakeExtractorFactory.failing(failure));

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitError(TIMEOUT)).isSameAs(failure);
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.FAILED);
    assertThat(harness.connector.state()).isEqualTo(ConnectorState.FAILED);
    assertNoFirstUnitTimer(harness);
    assertSessionEventually(harness, "error", "FAILURE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void extractorStartFailureReportsFailedSessionWithoutFirstUnitMetric() throws Exception {
    var extractor =
        FakeExtractor.failingStart(new RtspKlvExtractorException("start failure", null));
    var harness = Harness.withExtractor(extractor);

    harness.connector.start();

    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.CONNECTING);
    assertThat(harness.listener.awaitError(TIMEOUT))
        .isInstanceOf(RtspKlvExtractorException.class)
        .hasMessage("start failure");
    assertThat(harness.listener.awaitState(TIMEOUT)).isEqualTo(ConnectorState.FAILED);
    assertThat(harness.connector.state()).isEqualTo(ConnectorState.FAILED);
    assertThat(extractor.closed()).isTrue();
    assertNoFirstUnitTimer(harness);
    assertSessionEventually(harness, "error", "FAILURE");
    assertActiveStreamsEventually(harness, 0.0d);
  }

  @Test
  void sendIsUnsupported() {
    var harness = Harness.withPackets();

    assertThatThrownBy(() -> harness.connector.send(new byte[] {1}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static void assertSessionEventually(Harness harness, String outcome, String reason)
      throws InterruptedException {
    waitUntil(
        () -> sessionCount(harness, outcome, reason) == 1.0d,
        () ->
            assertThat(sessionCount(harness, outcome, reason))
                .as("session count outcome=%s reason=%s", outcome, reason)
                .isEqualTo(1.0d));
  }

  private static void assertActiveStreamsEventually(Harness harness, double expected)
      throws InterruptedException {
    waitUntil(
        () -> activeStreams(harness) == expected,
        () -> assertThat(activeStreams(harness)).as("active streams").isEqualTo(expected));
  }

  private static void assertNoFirstUnitTimer(Harness harness) {
    assertThat(harness.meterRegistry.find(FIRST_UNIT_DURATION).timer()).isNull();
  }

  private static double sessionCount(Harness harness, String outcome, String reason) {
    var counter =
        harness
            .meterRegistry
            .find(SESSIONS_TOTAL)
            .tags("outcome", outcome, "reason", reason)
            .counter();
    return counter == null ? 0.0d : counter.count();
  }

  private static double activeStreams(Harness harness) {
    return harness.meterRegistry.get(ACTIVE_STREAMS).gauge().value();
  }

  private static void waitUntil(Condition condition, Runnable assertion)
      throws InterruptedException {
    var deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.matches()) {
        return;
      }
      Thread.sleep(10);
    }
    assertion.run();
  }

  private record Harness(
      RtspKlvConnector connector,
      RecordingConnectorListener listener,
      FakeExtractor extractor,
      SimpleMeterRegistry meterRegistry) {

    static Harness withPackets(byte[]... packets) {
      return withExtractor(new FakeExtractor(new ArrayDeque<>(List.of(packets)), false));
    }

    static Harness withExtractor(FakeExtractor extractor) {
      return withConfigAndExtractor(
          new RtspConfig("rtsp://localhost:8554/klv", null), Duration.ofMillis(250), extractor);
    }

    static Harness withFactory(RtspKlvExtractorFactory extractorFactory) {
      return withConfigAndFactory(
          new RtspConfig("rtsp://localhost:8554/klv", null),
          Duration.ofMillis(250),
          extractorFactory,
          null);
    }

    static Harness withConfigAndExtractor(
        RtspConfig config, Duration firstMetadataTimeout, FakeExtractor extractor) {
      return withConfigAndFactory(
          config, firstMetadataTimeout, new FakeExtractorFactory(extractor), extractor);
    }

    static Harness withConfigAndFactory(
        RtspConfig config,
        Duration firstMetadataTimeout,
        RtspKlvExtractorFactory extractorFactory,
        FakeExtractor extractor) {
      var listener = new RecordingConnectorListener();
      var meterRegistry = new SimpleMeterRegistry();
      var observability =
          new RtspKlvObservability(
              meterRegistry, GlobalOpenTelemetry.getTracer(RtspKlvObservability.TRACER_NAME));
      var connector =
          new RtspKlvConnector(
              UUID.randomUUID(),
              config,
              extractorFactory,
              new KlvPacketFramer(),
              firstMetadataTimeout,
              observability);
      connector.addListener(listener);
      return new Harness(connector, listener, extractor, meterRegistry);
    }
  }

  @FunctionalInterface
  private interface Condition {
    boolean matches();
  }

  private static final class FakeExtractorFactory extends RtspKlvExtractorFactory {
    private final FakeExtractor extractor;
    private final RuntimeException failure;

    FakeExtractorFactory(FakeExtractor extractor) {
      this(extractor, null);
    }

    private FakeExtractorFactory(FakeExtractor extractor, RuntimeException failure) {
      super(RtspConnectorDefaults.defaults());
      this.extractor = extractor;
      this.failure = failure;
    }

    static FakeExtractorFactory failing(RuntimeException failure) {
      return new FakeExtractorFactory(null, failure);
    }

    @Override
    public RtspKlvExtractor create(RtspConfig config) {
      if (failure != null) {
        throw failure;
      }
      return extractor;
    }
  }

  private static final class FakeExtractor implements RtspKlvExtractor {
    private final Queue<byte[]> packets;
    private final boolean repeatLastPacket;
    private final CountDownLatch startStarted = new CountDownLatch(1);
    private final CountDownLatch readStarted = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean blockStart;
    private final boolean blockReads;
    private final boolean filterUntilDeadline;
    private final RtspKlvExtractorException startFailure;
    private byte[] lastPacket;

    FakeExtractor(Queue<byte[]> packets, boolean repeatLastPacket) {
      this(packets, repeatLastPacket, false, false, false, null);
    }

    private FakeExtractor(
        Queue<byte[]> packets,
        boolean repeatLastPacket,
        boolean blockStart,
        boolean blockReads,
        boolean filterUntilDeadline,
        RtspKlvExtractorException startFailure) {
      this.packets = new ArrayDeque<>(packets);
      this.repeatLastPacket = repeatLastPacket;
      this.blockStart = blockStart;
      this.blockReads = blockReads;
      this.filterUntilDeadline = filterUntilDeadline;
      this.startFailure = startFailure;
    }

    static FakeExtractor blockingStart() {
      return new FakeExtractor(new ArrayDeque<>(), false, true, false, false, null);
    }

    static FakeExtractor blocking() {
      return new FakeExtractor(new ArrayDeque<>(), false, false, true, false, null);
    }

    static FakeExtractor failingStart(RtspKlvExtractorException failure) {
      return new FakeExtractor(new ArrayDeque<>(), false, false, false, false, failure);
    }

    static FakeExtractor filteringUntilDeadline() {
      return new FakeExtractor(new ArrayDeque<>(), false, false, false, true, null);
    }

    @Override
    public void start() throws RtspKlvExtractorException {
      startStarted.countDown();
      if (startFailure != null) {
        throw startFailure;
      }
      while (blockStart && !closed.get()) {
        sleep(Duration.ofMillis(10));
      }
    }

    @Override
    public byte[] readNextPacket(long deadlineNanos) throws RtspKlvExtractorException {
      readStarted.countDown();
      if (filterUntilDeadline) {
        while (!closed.get() && System.nanoTime() < deadlineNanos) {
          sleep(Duration.ofMillis(1));
        }
        throw new NoKlvMetadataException("RTSP source did not produce complete KLV metadata");
      }
      if (blockReads) {
        while (!closed.get()) {
          sleep(Duration.ofMillis(10));
        }
        throw new RtspKlvExtractorException("closed", null);
      }
      var next = packets.poll();
      if (next != null) {
        lastPacket = next;
        return next;
      }
      if (repeatLastPacket && lastPacket != null) {
        sleep(Duration.ofMillis(5));
        return lastPacket;
      }
      return null;
    }

    @Override
    public void close() {
      closed.set(true);
    }

    boolean awaitReadStarted() throws InterruptedException {
      return readStarted.await(1, TimeUnit.SECONDS);
    }

    boolean awaitStartStarted() throws InterruptedException {
      return startStarted.await(1, TimeUnit.SECONDS);
    }

    boolean closed() {
      return closed.get();
    }

    private static void sleep(Duration duration) throws RtspKlvExtractorException {
      try {
        Thread.sleep(duration.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RtspKlvExtractorException("interrupted", e);
      }
    }
  }

  private static byte[] klv(int payloadLength, int fill) {
    var out = new ByteArrayOutputStream();
    out.writeBytes(KlvPacketFramer.ST_0601_UNIVERSAL_KEY);
    out.write(payloadLength);
    for (int i = 0; i < payloadLength; i++) {
      out.write(fill);
    }
    return out.toByteArray();
  }

  private static byte[] concat(byte[]... parts) {
    var out = new ByteArrayOutputStream();
    for (var part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static byte[] slice(byte[] bytes, int from, int to) {
    var result = new byte[to - from];
    System.arraycopy(bytes, from, result, 0, result.length);
    return result;
  }
}
