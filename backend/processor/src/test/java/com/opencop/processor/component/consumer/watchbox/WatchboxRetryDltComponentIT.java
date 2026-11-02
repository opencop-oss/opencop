// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.technical.spring.consumer.WatchboxConsumer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Covers the Watchbox listener's poll-wide retry and dead-letter path.
 *
 * <p>The sibling byod and fixed-DLT component tests cover dead-letter <em>indexing</em>; none of
 * them exercise the Watchbox listener's own error handler, so a change to its retry count, backoff,
 * or DLT routing could not be caught. This test closes that gap.
 *
 * <p>It runs through production wiring — the real {@code MessageBrokerConfig} builds the factory,
 * so the {@code DefaultErrorHandler}, the {@code ExponentialBackOffWithMaxRetries}, and the {@code
 * DeadLetterPublishingRecoverer} are the real configured instances rather than test-declared ones.
 * The always-throwing handler is the only substitution.
 *
 * <p>Contract being pinned, from {@code KafkaListenerFactoryBuilder} plus the {@code watchbox}
 * listener config: batch mode, {@code maxRetries=3}, exponential backoff from 500 ms ×2.0, then
 * publish to {@code <topic>.dlt}. A failing batch must therefore be attempted more than once, land
 * on the DLT topic, and leave the listener alive to keep consuming. The poison batch throws {@code
 * IllegalStateException} — a deterministic poison class — so {@code retryInPlaceFailures} stays out
 * of the way and the ordinary exhaust-then-dead-letter ladder is what runs.
 */
@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      WatchboxComponentTestContext.class,
      WatchboxRetryDltComponentIT.ThrowingHandlerConfig.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 2,
    topics = {
      "${opencop.messaging-broker.listeners.watchbox.topic}",
      "${opencop.messaging-broker.listeners.watchbox.topic}.dlt"
    })
class WatchboxRetryDltComponentIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private KafkaListenerEndpointRegistry registry;
  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private AtomicInteger watchboxHandlerAttempts;

  @Value("${opencop.messaging-broker.listeners.watchbox.topic}")
  private String watchboxTopic;

  @Test
  void aPoisonBatchIsRetriedThenDeadLetteredAndTheListenerSurvives() {
    ContainerTestUtils.waitForAssignment(
        registry.getListenerContainer(WatchboxConsumer.LISTENER_ID),
        broker.getPartitionsPerTopic());

    featureKafkaTemplate.send(watchboxTopic, "poison", feature());
    featureKafkaTemplate.flush();

    // The handler throws every time, so the error handler must exhaust its backoff before
    // recovering. maxRetries=3 means one initial attempt plus three retries.
    await(() -> watchboxHandlerAttempts.get() >= 4, "the failing batch is retried, not dropped");
    assertThat(watchboxHandlerAttempts.get())
        .as("maxRetries=3 means exactly one initial attempt plus three retries")
        .isEqualTo(4);

    try (var dltConsumer = dltConsumer()) {
      dltConsumer.subscribe(List.of(watchboxTopic + ".dlt"));
      var deadline = System.nanoTime() + TIMEOUT.toNanos();
      int seen = 0;
      while (seen == 0 && System.nanoTime() < deadline) {
        seen = dltConsumer.poll(Duration.ofMillis(500)).count();
      }
      assertThat(seen)
          .as(
              "the exhausted batch must be published to %s.dlt, not silently discarded",
              watchboxTopic)
          .isPositive();
    }

    assertThat(registry.getListenerContainer(WatchboxConsumer.LISTENER_ID).isRunning())
        .as("dead-lettering must not kill the listener; it has to keep consuming afterwards")
        .isTrue();
  }

  private KafkaConsumer<String, byte[]> dltConsumer() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "watchbox-dlt-probe");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new KafkaConsumer<>(props);
  }

  private static Feature feature() {
    var feature = new Feature();
    feature.setGeometry(new Point(1.0, 2.0));
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

  /** The listener's real error handler is the thing under test; the handler always throws. */
  @TestConfiguration
  static class ThrowingHandlerConfig {

    @Bean
    AtomicInteger watchboxHandlerAttempts() {
      return new AtomicInteger();
    }

    @Bean
    WatchboxEventHandler throwingWatchboxEventHandler(AtomicInteger watchboxHandlerAttempts) {
      var handler = mock(WatchboxEventHandler.class);
      doAnswer(
              invocation -> {
                watchboxHandlerAttempts.incrementAndGet();
                throw new IllegalStateException("poison batch");
              })
          .when(handler)
          .process(anyList());
      return handler;
    }
  }
}
