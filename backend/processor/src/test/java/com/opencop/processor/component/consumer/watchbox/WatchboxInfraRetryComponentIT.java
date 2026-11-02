// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.watchbox;

import static com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedTestSupport.generationChanged;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Covers the Watchbox listener's retained-batch contract: infrastructure outages and
 * database-proven generation churn retry in place until the transient condition clears, and are
 * never dead-lettered. The Watchbox DLT is a tombstone, not a recovery queue, because the monotonic
 * fold and live-window guard make later replay ineffective.
 *
 * <p>Runs through production wiring like {@link WatchboxRetryDltComponentIT}, whose poison-batch
 * test pins the complementary half: a deterministic failure still exhausts the production {@code
 * maxRetries=3} and dead-letters the whole batch. This class overrides that ceiling to one retry
 * solely to reach the same policy boundary faster.
 *
 * <p>Before {@code retryInPlaceFailures}, a database interruption outlasting the ~3.5s ordinary
 * retry ladder dead-lettered up to {@code maxPollRecords} events with offsets committed. A terminal
 * generation mismatch took that same path until the generation signal joined this policy.
 *
 * <p>This IT is also the behavioral floor guard for spring-kafka >= 4.0.6 (3.3.16): retry-in-place
 * needs {@code FallbackBatchErrorHandler} to consult the per-exception backOffFunction and {@code
 * FailedBatchProcessor} to propagate {@code resetStateOnExceptionChange}. On 4.0.5 the identical
 * production code compiles and runs but the fallback handler uses only the constructor's
 * exponential ladder, so these batches dead-letter before their third-attempt recovery.
 */
@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      WatchboxComponentTestContext.class,
      WatchboxInfraRetryComponentIT.OutageThenRecoveryHandlerConfig.class
    },
    properties = "opencop.messaging-broker.listeners.watchbox.maxRetries=1")
@DirtiesContext
@EmbeddedKafka(
    partitions = 2,
    topics = {
      "${opencop.messaging-broker.listeners.watchbox.topic}",
      "${opencop.messaging-broker.listeners.watchbox.topic}.dlt"
    })
class WatchboxInfraRetryComponentIT {

  /** The overridden poison path survives two failed deliveries; recovery needs the third. */
  private static final int TRANSIENT_FAILURE_ATTEMPTS = 2;

  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private KafkaListenerEndpointRegistry registry;
  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private RetryAttempts retryAttempts;

  @Value("${opencop.messaging-broker.listeners.watchbox.topic}")
  private String watchboxTopic;

  @Test
  void anInfrastructureOutageIsRetriedUntilRecoveryAndNothingIsDeadLettered() {
    assertRetriedUntilRecovery(
        feature("infrastructure"), retryAttempts.infrastructure, "infra-retry-probe");
  }

  @Test
  void aGenerationChangeIsRetriedUntilStableAndNothingIsDeadLettered() {
    assertRetriedUntilRecovery(
        feature("generation"), retryAttempts.generation, "generation-retry-probe");
  }

  private void assertRetriedUntilRecovery(
      Feature feature, AtomicInteger attempts, String dltConsumerGroup) {
    ContainerTestUtils.waitForAssignment(
        registry.getListenerContainer(WatchboxConsumer.LISTENER_ID),
        broker.getPartitionsPerTopic());

    featureKafkaTemplate.send(watchboxTopic, dltConsumerGroup, feature);
    featureKafkaTemplate.flush();

    // Both configured poison-path deliveries fail; reaching success on attempt three proves the
    // retained-batch policy outlived the point where ordinary retry would publish to DLT.
    await(
        () -> attempts.get() >= TRANSIENT_FAILURE_ATTEMPTS + 1,
        "the batch is retained past the ordinary dead-letter point");

    try (var dltConsumer = dltConsumer(dltConsumerGroup)) {
      dltConsumer.subscribe(List.of(watchboxTopic + ".dlt"));
      var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      var seen = 0;
      while (System.nanoTime() < deadline) {
        seen += dltConsumer.poll(Duration.ofMillis(500)).count();
      }
      assertThat(seen)
          .as("a retained transient failure must not publish to %s.dlt", watchboxTopic)
          .isZero();
    }

    assertThat(registry.getListenerContainer(WatchboxConsumer.LISTENER_ID).isRunning())
        .as("the listener must still be consuming after the outage heals")
        .isTrue();
  }

  private KafkaConsumer<String, byte[]> dltConsumer(String groupId) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new KafkaConsumer<>(props);
  }

  private static Feature feature(String retryKind) {
    var feature = new Feature();
    feature.setGeometry(new Point(1.0, 2.0));
    feature.setProperty("retry_kind", retryKind);
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
   * Simulates retained transient conditions that heal after more attempts than the poison path
   * permits.
   */
  @TestConfiguration
  static class OutageThenRecoveryHandlerConfig {

    @Bean
    RetryAttempts retryAttempts() {
      return new RetryAttempts();
    }

    @Bean
    WatchboxEventHandler outageThenRecoveryHandler(RetryAttempts retryAttempts) {
      var handler = mock(WatchboxEventHandler.class);
      doAnswer(
              invocation -> {
                List<Feature> features = invocation.getArgument(0);
                var retryKind = (String) features.getFirst().getProperty("retry_kind");
                if ("generation".equals(retryKind)) {
                  if (retryAttempts.generation.incrementAndGet() <= TRANSIENT_FAILURE_ATTEMPTS) {
                    throw generationChanged();
                  }
                } else if (retryAttempts.infrastructure.incrementAndGet()
                    <= TRANSIENT_FAILURE_ATTEMPTS) {
                  throw new DataAccessResourceFailureException("simulated database outage");
                }
                return null;
              })
          .when(handler)
          .process(anyList());
      return handler;
    }
  }

  static final class RetryAttempts {
    private final AtomicInteger infrastructure = new AtomicInteger();
    private final AtomicInteger generation = new AtomicInteger();
  }
}
