// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedTestSupport.generationChanged;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry.Reason;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

class WatchboxRetainedRetryTrackerTest {

  private static final String TOPIC = "opencop.geo.validated";
  private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
  private final MutableClock clock = new MutableClock(NOW);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final WatchboxRetainedRetryTracker tracker =
      new WatchboxRetainedRetryTracker(TOPIC, clock, new ProcessorMetrics(registry));

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    var logger = (Logger) LoggerFactory.getLogger(WatchboxRetainedRetryTracker.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    var logger = (Logger) LoggerFactory.getLogger(WatchboxRetainedRetryTracker.class);
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void retainedInfrastructureFailureBecomesAnActiveEpisodeWithAdvancingAttempts() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    clock.advance(Duration.ofSeconds(5));
    tracker.failedDelivery(records(0), infrastructureFailure(), 2);

    var aggregate = tracker.aggregate();

    assertThat(aggregate).isNotNull();
    assertThat(aggregate.partitions()).isEqualTo(1);
    assertThat(aggregate.reason()).isEqualTo(Reason.INFRASTRUCTURE);
    assertThat(aggregate.since()).isEqualTo(NOW);
    assertThat(aggregate.attempts()).isEqualTo(2);
  }

  @Test
  void generationChangeAnywhereInTheCauseChainMapsToGenerationChanged() {
    tracker.failedDelivery(
        records(0), new RuntimeException("listener wrapper", generationChanged()), 1);

    var aggregate = tracker.aggregate();

    assertThat(aggregate).isNotNull();
    assertThat(aggregate.reason()).isEqualTo(Reason.GENERATION_CHANGED);
  }

  @Test
  void ordinaryPoisonFailuresAreNotTracked() {
    // failedDelivery also fires for every attempt of the ordinary exponential ladder; a poison
    // batch resolves through the DLT path and must not masquerade as a liveness wedge.
    tracker.failedDelivery(records(0), new IllegalStateException("poison"), 1);

    assertThat(tracker.aggregate()).isNull();
  }

  @Test
  void episodesOnForeignTopicsAreIgnored() {
    var foreign = new TopicPartition("some.other.topic", 0);
    tracker.failedDelivery(
        new ConsumerRecords<>(
            Map.of(foreign, List.of(new ConsumerRecord<>("some.other.topic", 0, 0L, "k", "v")))),
        infrastructureFailure(),
        1);

    assertThat(tracker.aggregate()).isNull();
  }

  @Test
  void aggregateCountsEveryWedgedPartitionAndDescribesTheOldest() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    clock.advance(Duration.ofSeconds(10));
    tracker.failedDelivery(records(1), generationChanged(), 1);

    var aggregate = tracker.aggregate();

    assertThat(aggregate).isNotNull();
    assertThat(aggregate.partitions()).isEqualTo(2);
    // Oldest wedge wins the descriptive fields: partition 0 started first, with INFRASTRUCTURE.
    assertThat(aggregate.reason()).isEqualTo(Reason.INFRASTRUCTURE);
    assertThat(aggregate.since()).isEqualTo(NOW);
  }

  @Test
  void elapsedTimeAloneDoesNotResolveAnEpisode() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    clock.advance(Duration.ofDays(1));

    assertThat(tracker.aggregate()).isNotNull();
    assertThat(infoMessages()).isEmpty();
  }

  @Test
  void recoveredClearsOnlyTheExactWatchboxEpisode() {
    // The recoverer path is reachable when a mid-retry exception-class change reroutes the batch
    // to the ordinary ladder; its terminal completion is authoritative resolution evidence.
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    tracker.failedDelivery(records(1), infrastructureFailure(), 1);
    tracker.recovered(records(0), infrastructureFailure());

    assertThat(tracker.aggregate()).isNotNull();
    assertThat(tracker.aggregate().partitions()).isOne();
    assertThat(infoMessages())
        .singleElement()
        .asString()
        .contains("Watchbox retained retry resolved", "resolution=error_handler_completion");
  }

  @Test
  void batchSuccessClearsOnlyTheExactWatchboxPartitions() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    tracker.failedDelivery(records(1), infrastructureFailure(), 1);

    tracker.success(records(0), null);

    var aggregate = tracker.aggregate();

    assertThat(aggregate).isNotNull();
    assertThat(aggregate.partitions()).isOne();
    assertThat(infoMessages()).hasSize(1);
    assertThat(infoMessages().getFirst()).contains("resolution=batch_success");
  }

  @Test
  void assignmentLifecycleClearsOnlyNamedWatchboxPartitions() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    tracker.failedDelivery(records(1), infrastructureFailure(), 1);

    tracker.onPartitionsRevoked(List.of(new TopicPartition(TOPIC, 0)));
    assertThat(tracker.aggregate()).isNotNull();
    assertThat(tracker.aggregate().partitions()).isOne();

    tracker.onPartitionsLost(List.of(new TopicPartition(TOPIC, 1)));
    assertThat(tracker.aggregate()).isNull();

    tracker.failedDelivery(records(2), infrastructureFailure(), 1);
    tracker.onPartitionsAssigned(List.of(new TopicPartition(TOPIC, 2)));
    assertThat(tracker.aggregate()).isNull();
    assertThat(infoMessages())
        .allSatisfy(message -> assertThat(message).contains("resolution=assignment_change"));
  }

  @Test
  void foreignSuccessRecoveryAndAssignmentNeverClearWatchboxEpisodes() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    var foreign = new TopicPartition("some.other.topic", 0);
    var foreignRecords =
        new ConsumerRecords<>(
            Map.of(
                foreign,
                List.of(new ConsumerRecord<>(foreign.topic(), 0, 0L, "k", new Feature()))));

    tracker.success(foreignRecords, null);
    tracker.recovered(foreignRecords, infrastructureFailure());
    tracker.onPartitionsAssigned(List.of(foreign));

    assertThat(tracker.aggregate()).isNotNull();
    assertThat(tracker.aggregate().partitions()).isOne();
  }

  @Test
  void warnStreamIsEpisodeStartThenPeriodicSummaryWithSuppressedCount() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    for (var i = 0; i < 5; i++) {
      clock.advance(Duration.ofSeconds(5));
      tracker.failedDelivery(records(0), infrastructureFailure(), i + 2);
    }
    assertThat(warnMessages())
        .singleElement()
        .asString()
        .startsWith("Watchbox retained retry started");

    // Crossing the summary interval emits one WARN carrying the demoted repeat count.
    clock.advance(Duration.ofSeconds(40));
    tracker.failedDelivery(records(0), infrastructureFailure(), 7);
    assertThat(warnMessages()).hasSize(2);
    assertThat(warnMessages().get(1))
        .startsWith("Watchbox retained retry continuing")
        .contains("suppressedCount=5");
  }

  @Test
  void gaugesReportActivePartitionsPerReasonAndClearAfterResolution() {
    tracker.failedDelivery(records(0), infrastructureFailure(), 1);
    tracker.failedDelivery(records(1), generationChanged(), 1);
    tracker.aggregate();

    assertThat(gaugeValue("INFRASTRUCTURE")).isEqualTo(1.0);
    assertThat(gaugeValue("GENERATION_CHANGED")).isEqualTo(1.0);

    tracker.success(records(0), null);
    tracker.recovered(records(1), infrastructureFailure());
    tracker.aggregate();

    assertThat(gaugeValue("INFRASTRUCTURE")).isZero();
    assertThat(gaugeValue("GENERATION_CHANGED")).isZero();
  }

  private double gaugeValue(String reason) {
    return registry
        .get(WatchboxRetainedRetryTracker.RETAINED_RETRY_PARTITIONS_GAUGE)
        .tag("reason", reason)
        .gauge()
        .value();
  }

  private List<String> warnMessages() {
    return messagesAt(Level.WARN);
  }

  private List<String> infoMessages() {
    return messagesAt(Level.INFO);
  }

  private List<String> messagesAt(Level level) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static ConsumerRecords<String, Feature> records(int partition) {
    var topicPartition = new TopicPartition(TOPIC, partition);
    return new ConsumerRecords<>(
        Map.of(
            topicPartition,
            List.of(new ConsumerRecord<>(TOPIC, partition, 0L, "k", new Feature()))));
  }

  private static Exception infrastructureFailure() {
    return new DataAccessResourceFailureException("simulated database outage");
  }

  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant initial) {
      this.instant = initial;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
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
