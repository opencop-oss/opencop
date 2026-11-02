// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.consumer.WatchboxConsumer;
import com.opencop.processor.technical.spring.consumer.WatchboxDetectionHeartbeatPublisher;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import com.opencop.processor.technical.spring.dlt.FixedDeadLetterConsumer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves that the Processor's real Kafka wiring hands every dynamically registered listener bean
 * the Spring context callbacks it depends on, by importing the production {@link
 * MessageBrokerConfig} instead of declaring a listener factory of its own.
 *
 * <p>This test exists because the defect it pins was undetectable any other way. {@code
 * MessageBrokerConfig} registered its factories with a bare {@code registerSingleton}, which skips
 * every {@code BeanPostProcessor}; the factories therefore never received an {@code
 * ApplicationEventPublisher}, and Spring Kafka copies a publisher onto the containers it creates
 * only when its own is non-null. Containers ran and consumed while emitting no lifecycle events at
 * all. {@code WatchboxDetectionHeartbeatPublisher} takes its no-traffic liveness evidence
 * exclusively from {@code ListenerContainerIdleEvent}s, so a quiet system would read as
 * detection-down forever - yet a suite of self-wired tests stays green, because a test that
 * declares its own {@code @Bean("watchboxFactory")} does get the publisher. A test that declares
 * its own wiring cannot validate wiring; only importing the production assembly can.
 *
 * <p>The assertions are deliberately about the wiring the containers receive, not about how
 * registration is implemented, so they stay valid if the registration strategy changes again.
 *
 * <p>No broker is required and the bootstrap address is unroutable on purpose. The watchbox
 * listener auto-starts, so its containers do spin up here and poll the unroutable address until the
 * context closes; that is harmless connection-WARN noise, and {@code @DirtiesContext} bounds it to
 * this class instead of leaking polling threads into the cached-context lifetime of the whole
 * suite.
 */
@SpringJUnitConfig
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      // A representative slice of the production listener set rather than a copy of it. Both
      // valueType branches of the registration loop are covered, which is what this test is about;
      // the assertions then sweep whatever was registered, so they do not drift as listeners
      // are added or renamed in application.yml.
      "opencop.messaging-broker.listeners.watchbox.topic=opencop.geo.validated",
      "opencop.messaging-broker.listeners.watchbox.groupId=opencop-watchbox",
      "opencop.messaging-broker.listeners.watchbox.concurrency=2",
      "opencop.messaging-broker.listeners.watchbox.maxPollRecords=1000",
      "opencop.messaging-broker.listeners.watchbox.valueType=feature",
      "opencop.messaging-broker.listeners.watchbox.stage=WATCHBOX",
      "opencop.messaging-broker.listeners.watchbox.idleEventInterval=10s",
      // Mirrors application.yml: the retained-retry branch is where the tracker registration
      // this test pins actually happens.
      "opencop.messaging-broker.listeners.watchbox.retryInPlaceFailures=true",
      "opencop.messaging-broker.listeners.other.topic=opencop.geo.other",
      "opencop.messaging-broker.listeners.other.groupId=opencop-other-feature",
      "opencop.messaging-broker.listeners.other.concurrency=1",
      "opencop.messaging-broker.listeners.other.valueType=feature",
      "opencop.messaging-broker.listeners.other.stage=WATCHBOX",
      "opencop.messaging-broker.listeners.raw.topic=opencop.event.external.raw",
      "opencop.messaging-broker.listeners.raw.groupId=opencop-raw",
      "opencop.messaging-broker.listeners.raw.concurrency=1",
      "opencop.messaging-broker.listeners.raw.valueType=raw",
      "opencop.messaging-broker.listeners.raw.stage=RAW",
      // Enabled so the DLT index containers, the third registration site, are covered here too.
      "opencop.messaging-broker.dlt-indexing.enabled=true",
      "opencop.messaging-broker.dlt-indexing.enabled-listeners=raw",
    })
class MessageBrokerProductionWiringTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private KafkaListenerEndpointRegistry registry;

  @Test
  @DisplayName("the Watchbox container can publish the idle events the heartbeat lives on")
  void watchboxContainerCanPublishLifecycleEvents() {
    var container =
        (AbstractMessageListenerContainer<?, ?>)
            registry.getListenerContainer(WatchboxConsumer.LISTENER_ID);

    assertThat(container)
        .as("the production wiring must create a container for %s", WatchboxConsumer.LISTENER_ID)
        .isNotNull();
    assertThat(container.getApplicationEventPublisher())
        .as(
            "without a publisher the container emits no ListenerContainerIdleEvent, so the"
                + " detection heartbeat starves on a quiet topic and status reads detection-down"
                + " while the listener is healthy")
        .isNotNull();
  }

  @Test
  @DisplayName("every dynamically registered listener factory receives publisher and context")
  void everyRegisteredFactoryIsFullyInitialized() {
    var factories =
        applicationContext.getBeansOfType(ConcurrentKafkaListenerContainerFactory.class);

    assertThat(factories)
        .as("both branches of the registration loop must be exercised")
        .containsKeys("watchboxFactory", "rawFactory");

    // The factory exposes no getters for these, so read the fields the Aware callbacks populate.
    assertThat(factories)
        .allSatisfy(
            (name, factory) -> {
              assertThat(ReflectionTestUtils.getField(factory, "applicationEventPublisher"))
                  .as("%s must receive an ApplicationEventPublisher", name)
                  .isNotNull();
              assertThat(ReflectionTestUtils.getField(factory, "applicationContext"))
                  .as("%s must receive an ApplicationContext", name)
                  .isNotNull();
            });
  }

  @Test
  @DisplayName("only the watchbox container publishes idle events for the liveness heartbeat")
  void onlyWatchboxContainerPublishesIdleEvents() {
    var watchbox =
        (AbstractMessageListenerContainer<?, ?>)
            registry.getListenerContainer(WatchboxConsumer.LISTENER_ID);

    assertThat(watchbox.getContainerProperties().getIdleEventInterval())
        .as(
            "without idle events the detection heartbeat starves whenever no traffic flows and"
                + " status turns amber for a healthy quiet listener")
        .isEqualTo(10_000L);
    assertThat(watchbox.getContainerProperties().getIdleBeforeDataMultiplier())
        .as(
            "the default 5x pre-first-record multiplier stretches idle events to 50s on a topic"
                + " that has never received a record, leaving almost no margin against the"
                + " heartbeat's 60s TTL on a quiet system")
        .isEqualTo(1.0);

    // Every other listener leaves idleEventInterval unset and publishes no idle events, so the
    // heartbeat's listener-id filter is defensive rather than load-bearing.
    registry
        .getListenerContainers()
        .forEach(
            container -> {
              if (container != watchbox) {
                assertThat(container.getContainerProperties().getIdleEventInterval()).isNull();
              }
            });
  }

  @Test
  @DisplayName(
      "the watchbox error handler notifies the retained-retry tracker on retained attempts")
  void watchboxErrorHandlerCarriesTheRetainedRetryTracker() {
    var factory =
        applicationContext.getBean(
            "watchboxFactory", ConcurrentKafkaListenerContainerFactory.class);

    // No getters anywhere on this path, so the assertion reads the fields the setters populate:
    // DefaultErrorHandler.setRetryListeners must forward to the fallback batch handler, because
    // that handler's retry loop is the only code that notifies listeners for retained batches. A
    // handler wired without the tracker compiles, runs, and retries correctly - it just never
    // produces a wedge fact, which is exactly the silent-divergence class this test exists for.
    var handler = ReflectionTestUtils.getField(factory, "commonErrorHandler");
    var fallbackHandler = ReflectionTestUtils.getField(handler, "fallbackBatchHandler");
    @SuppressWarnings("unchecked")
    var retryListeners =
        (List<Object>) ReflectionTestUtils.getField(fallbackHandler, "retryListeners");

    assertThat(retryListeners)
        .as(
            "without the tracker on the fallback batch handler, a wedged partition never reaches"
                + " the heartbeat row and status stays green through the outage")
        .containsExactly(applicationContext.getBean(WatchboxRetainedRetryTracker.class));
  }

  @Test
  @DisplayName("only the retry-in-place Watchbox factory receives tracker completion callbacks")
  void onlyWatchboxFactoryReceivesTheTrackerAsEveryCompletionCallbackRole() {
    var tracker = applicationContext.getBean(WatchboxRetainedRetryTracker.class);
    var watchbox =
        applicationContext.getBean(
            "watchboxFactory", ConcurrentKafkaListenerContainerFactory.class);
    var otherFeature =
        applicationContext.getBean("otherFactory", ConcurrentKafkaListenerContainerFactory.class);

    assertThat(watchbox.getBatchInterceptor()).isSameAs(tracker);
    assertThat(watchbox.getContainerProperties().getConsumerRebalanceListener()).isSameAs(tracker);
    assertThat(otherFeature.getBatchInterceptor()).isNull();
    assertThat(otherFeature.getContainerProperties().getConsumerRebalanceListener()).isNull();
  }

  @Test
  @DisplayName("watchbox container group id comes from configuration, not the listener id")
  void watchboxContainerUsesConfiguredGroupId() {
    var watchbox =
        (AbstractMessageListenerContainer<?, ?>)
            registry.getListenerContainer(WatchboxConsumer.LISTENER_ID);

    // @KafkaListener defaults idIsGroup=true: without an explicit groupId binding the listener id
    // silently becomes the consumer group and the configured groupId is ineffective. Group
    // identity is effectively deploy-once (a later rename orphans committed offsets), so this
    // pins it while no deployment exists.
    assertThat(watchbox.getGroupId()).isEqualTo("opencop-watchbox");
  }

  @Test
  @DisplayName("containers receive the ApplicationContext that listener timers depend on")
  void containersReceiveApplicationContext() {
    var container =
        (AbstractMessageListenerContainer<?, ?>)
            registry.getListenerContainer(WatchboxConsumer.LISTENER_ID);

    assertThat(ReflectionTestUtils.getField(container, "applicationContext"))
        .as("MicrometerHolder needs the ApplicationContext, or listener timers are silently lost")
        .isNotNull();
  }

  @Test
  @DisplayName("DLT index containers receive publisher and context")
  void dltIndexContainersAreFullyInitialized() {
    var dltContainers =
        applicationContext
            .getBeansOfType(AbstractMessageListenerContainer.class)
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().endsWith("DltIndexContainer"))
            .toList();

    assertThat(dltContainers)
        .as("dlt-indexing is enabled for the raw listener, so a DLT index container must exist")
        .isNotEmpty();

    assertThat(dltContainers)
        .allSatisfy(
            entry -> {
              assertThat(entry.getValue().getApplicationEventPublisher())
                  .as("%s must receive an ApplicationEventPublisher", entry.getKey())
                  .isNotNull();
              assertThat(ReflectionTestUtils.getField(entry.getValue(), "applicationContext"))
                  .as("%s must receive an ApplicationContext", entry.getKey())
                  .isNotNull();
            });
  }

  @Configuration
  @Import({MessageBrokerConfig.class, WatchboxConsumer.class})
  static class ProductionWiringContext {

    /** Unroutable on purpose: nothing in this test needs a reachable broker. */
    @Bean
    BaseKafkaConfig baseKafkaConfig() {
      return new BaseKafkaConfig(Map.of("bootstrap.servers", "localhost:1"));
    }

    @Bean
    WatchboxDetectionHeartbeatWriter watchboxDetectionHeartbeatWriter() {
      return new WatchboxDetectionHeartbeatWriter() {
        @Override
        public void writeHeartbeat(WatchboxDetectionHeartbeat heartbeat, Duration ttl) {}

        @Override
        public void markStopped(String instanceId) {}
      };
    }

    @Bean
    Clock productionWiringClock() {
      return Clock.systemUTC();
    }

    @Bean
    WatchboxRetainedRetryTracker watchboxRetainedRetryTracker(
        ProcessorMetrics metrics, Clock clock) {
      return new WatchboxRetainedRetryTracker("opencop.geo.validated", clock, metrics);
    }

    @Bean
    WatchboxDetectionHeartbeatPublisher watchboxDetectionHeartbeatPublisher(
        WatchboxDetectionHeartbeatWriter writer,
        ObjectProvider<KafkaListenerEndpointRegistry> registry,
        WatchboxRetainedRetryTracker tracker) {
      return new WatchboxDetectionHeartbeatPublisher(writer, registry, tracker);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    DeadLetterIndexingErrorHandlerFactory deadLetterIndexingErrorHandlerFactory() {
      return new DeadLetterIndexingErrorHandlerFactory(mock(DeadLetterObservability.class));
    }

    @Bean
    FixedDeadLetterConsumer fixedDeadLetterConsumer() {
      return mock(FixedDeadLetterConsumer.class);
    }

    @Bean
    WatchboxEventHandler watchboxEventHandler() {
      return mock(WatchboxEventHandler.class);
    }

    @Bean
    ProcessorMetrics processorMetrics() {
      return ProcessorMetrics.noop();
    }
  }
}
