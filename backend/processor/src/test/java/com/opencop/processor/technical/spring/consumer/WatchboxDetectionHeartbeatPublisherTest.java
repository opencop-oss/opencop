// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.WatchboxRetainedRetryTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.MessageListenerContainer;

class WatchboxDetectionHeartbeatPublisherTest {

  private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
  private static final String WATCHBOX_TOPIC = "opencop.geo.validated";

  private final RecordingWriter writer = new RecordingWriter();
  private final MutableClock clock = new MutableClock(NOW);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final WatchboxRetainedRetryTracker tracker =
      new WatchboxRetainedRetryTracker(WATCHBOX_TOPIC, clock, new ProcessorMetrics(meterRegistry));

  private WatchboxDetectionHeartbeatPublisher publisher(KafkaListenerEndpointRegistry registry) {
    return new WatchboxDetectionHeartbeatPublisher(
        writer, () -> registry, tracker, "processor-test", clock, null);
  }

  @Test
  void flushWritesOnlyWhenFreshEvidenceExists() {
    var publisher = publisher(null);

    // No evidence yet: nothing proves the poll loop turns, so nothing may refresh the row.
    publisher.flush();
    assertThat(writer.heartbeats).isEmpty();

    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats).hasSize(1);
    assertThat(writer.heartbeats.getFirst().heartbeat().lastEvaluatedAt()).isEqualTo(NOW);
    assertThat(writer.heartbeats.getFirst().ttl())
        .isEqualTo(WatchboxDetectionHeartbeatPublisher.HEARTBEAT_TTL);

    // Same evidence again: a timer-driven refresh would report a wedged consumer healthy.
    publisher.flush();
    assertThat(writer.heartbeats).hasSize(1);

    clock.set(NOW.plusSeconds(10));
    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats).hasSize(2);
    assertThat(writer.heartbeats.get(1).heartbeat().heartbeatSequence()).isEqualTo(2);
  }

  @Test
  void idleEventFromWatchboxChildIsEvidenceWithoutAnEvaluationStamp() {
    var container = mock(MessageListenerContainer.class);
    var publisher = publisher(null);

    publisher.onListenerIdle(
        new ListenerContainerIdleEvent(
            container,
            container,
            10_000L,
            WatchboxConsumer.LISTENER_ID + "-0",
            List.of(),
            null,
            false));
    publisher.flush();

    assertThat(writer.heartbeats).hasSize(1);
    // Idle proves the poll loop is turning; it says nothing about records being evaluated.
    assertThat(writer.heartbeats.getFirst().heartbeat().lastEvaluatedAt()).isNull();
  }

  @Test
  void idleEventFromAForeignListenerIsIgnored() {
    var container = mock(MessageListenerContainer.class);
    var publisher = publisher(null);

    publisher.onListenerIdle(
        new ListenerContainerIdleEvent(
            container, container, 10_000L, "persistConsumer-0", List.of(), null, false));
    publisher.flush();

    assertThat(writer.heartbeats).isEmpty();
  }

  @Test
  void heartbeatCarriesTheContainersAssignedPartitionCount() {
    var registry = mock(KafkaListenerEndpointRegistry.class);
    var container = mock(MessageListenerContainer.class);
    when(registry.getListenerContainer(WatchboxConsumer.LISTENER_ID)).thenReturn(container);
    when(container.getAssignedPartitions())
        .thenReturn(
            List.of(
                new TopicPartition("opencop.geo.validated", 0),
                new TopicPartition("opencop.geo.validated", 1)));
    var publisher = publisher(registry);

    publisher.recordBatchProcessed();
    publisher.flush();

    // An idle event proves polling but not assignment: a consumer with zero assignment polls and
    // reads healthy. The assignment count is what makes green mean something.
    assertThat(writer.heartbeats.getFirst().heartbeat().assignedPartitions()).isEqualTo(2);
  }

  @Test
  void flushCarriesTheTrackerAggregateIntoTheRow() {
    var publisher = publisher(null);
    tracker.failedDelivery(
        wedgedRecords(0), new DataAccessResourceFailureException("database outage"), 1);
    clock.set(NOW.plusSeconds(5));
    tracker.failedDelivery(
        wedgedRecords(0), new DataAccessResourceFailureException("database outage"), 2);

    publisher.recordBatchProcessed();
    publisher.flush();

    var retainedRetry = writer.heartbeats.getFirst().heartbeat().retainedRetry();
    assertThat(retainedRetry).isNotNull();
    assertThat(retainedRetry.partitions()).isEqualTo(1);
    assertThat(retainedRetry.reason()).isEqualTo(RetainedRetry.Reason.INFRASTRUCTURE);
    assertThat(retainedRetry.since()).isEqualTo(NOW);
    assertThat(retainedRetry.attempts()).isEqualTo(2);

    // Elapsed time does not make a retained retry healthy; only an explicit completion does.
    clock.set(NOW.plusSeconds(120));
    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats.get(1).heartbeat().retainedRetry()).isNotNull();

    tracker.recovered(wedgedRecords(0), new DataAccessResourceFailureException("database outage"));
    clock.set(NOW.plusSeconds(121));
    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats.get(2).heartbeat().retainedRetry()).isNull();
  }

  @Test
  void gaugesRefreshOnEveryTickEvenWithoutEvidence() {
    // A total wedge produces no listener evidence, so no row is written and the row ages toward
    // NOT_RUNNING - but the flush tick must still refresh the gauges so operators see live wedge
    // counts through the outage.
    var publisher = publisher(null);
    tracker.failedDelivery(
        wedgedRecords(0), new DataAccessResourceFailureException("database outage"), 1);

    publisher.flush();

    assertThat(writer.heartbeats).isEmpty();
    assertThat(
            meterRegistry
                .get(WatchboxRetainedRetryTracker.RETAINED_RETRY_PARTITIONS_GAUGE)
                .tag("reason", "INFRASTRUCTURE")
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  @Test
  void terminalIntegrityFailureDropsTheObservationInsteadOfSpinning() {
    var publisher = publisher(null);
    writer.failWith = new DataIntegrityViolationException("boom");

    publisher.recordBatchProcessed();
    publisher.flush();
    writer.failWith = null;
    publisher.flush();

    // The failed observation was dropped, not retried; only new evidence writes again.
    assertThat(writer.heartbeats).isEmpty();
    clock.set(NOW.plusSeconds(10));
    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats).hasSize(1);
  }

  @Test
  void transientWriteFailureRetriesOnTheNextFlush() {
    var publisher = publisher(null);
    writer.failWith = new RuntimeException("connection reset");

    publisher.recordBatchProcessed();
    publisher.flush();
    assertThat(writer.heartbeats).isEmpty();

    writer.failWith = null;
    publisher.flush();
    assertThat(writer.heartbeats).hasSize(1);
  }

  @Test
  void stopMarksTheRowStopped() {
    var publisher = publisher(null);
    publisher.stop();
    assertThat(writer.stoppedInstanceIds).containsExactly("processor-test");
  }

  @Test
  void noHeartbeatMayBeWrittenAfterStop() {
    // The repository's upsert deliberately revives stopped rows (a restarted container reuses its
    // instance id), so the stop marker being this process's final write is what keeps a straggler
    // flush from resurrecting a row Core is supposed to read as dead.
    var publisher = publisher(null);
    publisher.recordBatchProcessed();

    publisher.stop();
    publisher.flush();

    assertThat(writer.heartbeats).isEmpty();
    assertThat(writer.stoppedInstanceIds).containsExactly("processor-test");
  }

  private static ConsumerRecords<String, String> wedgedRecords(int partition) {
    var topicPartition = new TopicPartition(WATCHBOX_TOPIC, partition);
    return new ConsumerRecords<>(
        Map.of(
            topicPartition,
            List.of(new ConsumerRecord<>(WATCHBOX_TOPIC, partition, 0L, "k", "v"))));
  }

  private static final class RecordingWriter implements WatchboxDetectionHeartbeatWriter {
    private final List<Write> heartbeats = new ArrayList<>();
    private final List<String> stoppedInstanceIds = new ArrayList<>();
    private RuntimeException failWith;

    @Override
    public void writeHeartbeat(WatchboxDetectionHeartbeat heartbeat, Duration ttl) {
      if (failWith != null) {
        throw failWith;
      }
      heartbeats.add(new Write(heartbeat, ttl));
    }

    @Override
    public void markStopped(String instanceId) {
      stoppedInstanceIds.add(instanceId);
    }

    private record Write(WatchboxDetectionHeartbeat heartbeat, Duration ttl) {}
  }

  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant initial) {
      this.instant = initial;
    }

    private void set(Instant next) {
      instant = next;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
