// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry;
import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedTestSupport;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.technical.spring.consumer.WatchboxConsumer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Covers retained-retry liveness: heartbeat evidence is instance-wide, so at {@code concurrency=2}
 * one child wedged in retained retry-in-place is masked by its sibling's evidence and status stays
 * green while alerts from the wedged partition silently stop. The retained-retry facts on the
 * heartbeat row are the fix, and this IT proves them end to end through production wiring: real
 * {@code MessageBrokerConfig}, real error handler, real tracker, real publisher scheduler - only
 * the event handler and the heartbeat writer are test substitutes.
 *
 * <p>Records are sent to explicit partitions for determinism: with two children over two partitions
 * each child owns exactly one, so the partition-0 wedge and the partition-1 healthy path never
 * share a batch.
 *
 * <p>The retained retry blocks after its first failure while the healthy child continues. Advancing
 * the tracker's clock beyond the former silence bound proves that time alone cannot clear the
 * episode; releasing the retained retry proves Spring Kafka's real successful-batch callback does.
 */
@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      WatchboxComponentTestContext.class,
      WatchboxRetainedRetryLivenessComponentIT.WedgedPartitionHandlerConfig.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 2,
    topics = {
      "${opencop.messaging-broker.listeners.watchbox.topic}",
      "${opencop.messaging-broker.listeners.watchbox.topic}.dlt"
    })
class WatchboxRetainedRetryLivenessComponentIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  private static final int WEDGED_PARTITION = 0;
  private static final int HEALTHY_PARTITION = 1;

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private KafkaListenerEndpointRegistry registry;
  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private RetryControl retryControl;
  @Autowired private MutableClock trackerClock;

  @Autowired
  private WatchboxComponentTestContext.RecordingWatchboxDetectionHeartbeatWriter heartbeatWriter;

  @Value("${opencop.messaging-broker.listeners.watchbox.topic}")
  private String watchboxTopic;

  @Test
  void aBlockedRetainedRetrySurvivesElapsedTimeAndClearsAfterBatchSuccess() {
    var container =
        (ConcurrentMessageListenerContainer<?, ?>)
            registry.getListenerContainer(WatchboxConsumer.LISTENER_ID);
    ContainerTestUtils.waitForAssignment(container, broker.getPartitionsPerTopic());
    // waitForAssignment proves only the total: the group passes through a state where one child
    // owns both partitions, and sending then puts the wedge and healthy records into ONE batch -
    // wedging both partitions at once and starving the heartbeat of all evidence. The settled
    // one-partition-per-child split is the masked-sibling topology this IT exists to prove, and
    // it cannot collapse mid-test: the retry loop polls the paused consumer, so a wedge never
    // trips max.poll.interval into a rebalance.
    await(
        () ->
            container.getContainers().stream()
                .allMatch(
                    child -> {
                      var assigned = child.getAssignedPartitions();
                      return assigned != null && assigned.size() == 1;
                    }),
        "each consumer child to own exactly one partition");

    featureKafkaTemplate.send(watchboxTopic, WEDGED_PARTITION, "wedged", feature(true));
    featureKafkaTemplate.send(watchboxTopic, HEALTHY_PARTITION, "healthy", feature(false));
    featureKafkaTemplate.flush();

    try {
      retryControl.awaitBlocked();

      // Partition 1 supplies fresh proof after the clock jump, so this is not merely inspecting an
      // old row. A blocked retry has emitted no new failure callback, yet its fact remains active.
      trackerClock.advance(Duration.ofMinutes(2));
      var heartbeatsBeforeAdvance = heartbeatWriter.heartbeats().size();
      featureKafkaTemplate.send(
          watchboxTopic, HEALTHY_PARTITION, "healthy-after-advance", feature(false));
      featureKafkaTemplate.flush();
      await(
          () ->
              heartbeatWriter.heartbeats().stream()
                  .skip(heartbeatsBeforeAdvance)
                  .anyMatch(WatchboxRetainedRetryLivenessComponentIT::carriesWedgeFacts),
          "a post-advance healthy-partition heartbeat carrying the blocked partition's facts");

      var heartbeatsBeforeRelease = heartbeatWriter.heartbeats().size();
      retryControl.healAndRelease();
      await(
          () ->
              heartbeatWriter.heartbeats().stream()
                  .skip(heartbeatsBeforeRelease)
                  .anyMatch(heartbeat -> heartbeat.retainedRetry() == null),
          "a successful-retry heartbeat without retained-retry facts");

      try (var dltConsumer = dltConsumer()) {
        dltConsumer.subscribe(List.of(watchboxTopic + ".dlt"));
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        var seen = 0;
        while (System.nanoTime() < deadline) {
          seen += dltConsumer.poll(Duration.ofMillis(500)).count();
        }
        assertThat(seen)
            .as("a retained wedge must never publish to %s.dlt", watchboxTopic)
            .isZero();
      }

      assertThat(registry.getListenerContainer(WatchboxConsumer.LISTENER_ID).isRunning())
          .as("the listener must still be consuming after the wedge heals")
          .isTrue();
    } finally {
      retryControl.healAndRelease();
    }
  }

  private static boolean carriesWedgeFacts(WatchboxDetectionHeartbeat heartbeat) {
    var retainedRetry = heartbeat.retainedRetry();
    return retainedRetry != null
        && retainedRetry.partitions() == 1
        && retainedRetry.reason() == RetainedRetry.Reason.GENERATION_CHANGED;
  }

  private KafkaConsumer<String, byte[]> dltConsumer() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "retained-retry-liveness-probe");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new KafkaConsumer<>(props);
  }

  private static Feature feature(boolean wedge) {
    var feature = new Feature();
    feature.setGeometry(new Point(1.0, 2.0));
    feature.setProperty("wedge", wedge);
    return feature;
  }

  private static void await(BooleanSupplier condition, String what) {
    var deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted awaiting " + what, exception);
      }
    }
    throw new AssertionError("timed out awaiting " + what);
  }

  /**
   * Fails the first wedged batch, then blocks its retained retry until the test releases it;
   * everything else processes normally.
   */
  @TestConfiguration
  static class WedgedPartitionHandlerConfig {

    @Bean
    RetryControl retryControl() {
      return new RetryControl();
    }

    @Bean
    @Primary
    MutableClock retainedRetryTrackerClock() {
      return new MutableClock(Instant.parse("2026-08-24T12:00:00Z"));
    }

    @Bean
    WatchboxEventHandler wedgedPartitionHandler(RetryControl retryControl) {
      var handler = mock(WatchboxEventHandler.class);
      doAnswer(
              invocation -> {
                List<Feature> features = invocation.getArgument(0);
                var wedged =
                    features.stream()
                        .anyMatch(feature -> Boolean.TRUE.equals(feature.getProperty("wedge")));
                if (wedged && !retryControl.healed()) {
                  if (retryControl.attempts().incrementAndGet() > 1) {
                    retryControl.blockUntilReleased();
                  }
                  if (!retryControl.healed()) {
                    throw WatchboxGenerationChangedTestSupport.generationChanged();
                  }
                }
                return null;
              })
          .when(handler)
          .process(anyList());
      return handler;
    }
  }

  static final class RetryControl {
    private final AtomicInteger attempts = new AtomicInteger();
    private final CountDownLatch retryBlocked = new CountDownLatch(1);
    private final CountDownLatch releaseRetry = new CountDownLatch(1);
    private volatile boolean healed;

    AtomicInteger attempts() {
      return attempts;
    }

    boolean healed() {
      return healed;
    }

    void awaitBlocked() {
      try {
        if (!retryBlocked.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
          throw new AssertionError("timed out awaiting the retained retry to block");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "interrupted awaiting the retained retry to block", exception);
      }
    }

    void blockUntilReleased() {
      retryBlocked.countDown();
      try {
        if (!releaseRetry.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
          throw new IllegalStateException("timed out waiting to release the retained retry");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "interrupted while the retained retry was blocked", exception);
      }
    }

    void healAndRelease() {
      healed = true;
      releaseRetry.countDown();
    }
  }

  static final class MutableClock extends Clock {
    private volatile Instant instant;

    MutableClock(Instant initial) {
      this.instant = initial;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
