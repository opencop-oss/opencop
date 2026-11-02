// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.byod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.component.support.AbstractProcessorKafkaComponentIT;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.spring.config.byod.ByodListenerContainerFactory;
import com.opencop.processor.technical.spring.consumer.ByodMessageHandler;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      ByodMessageHandlerComponentTestContext.class
    },
    properties = {
      "opencop.byod.consumer.retry-attempts=1",
      "opencop.byod.consumer.retry-backoff-ms=0"
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "opencop.byod.component.2db183bf-1a15-4569-b471-47ed8ad2a1fe",
      "opencop.byod.component.2db183bf-1a15-4569-b471-47ed8ad2a1fe.dlt"
    })
class ByodMessageHandlerComponentIT extends AbstractProcessorKafkaComponentIT {

  private static final UUID SOURCE_ID = UUID.fromString("2db183bf-1a15-4569-b471-47ed8ad2a1fe");
  private static final String TOPIC = "opencop.byod.component.2db183bf-1a15-4569-b471-47ed8ad2a1fe";
  private static final String DLT_TOPIC = TOPIC + KafkaNaming.DLT_SUFFIX;

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private EventDecoderRegistry eventDecoderRegistry;
  @Autowired private RecordingFeatureProducer recordingFeatureProducer;
  @Autowired private ByodListenerContainerFactory byodListenerContainerFactory;
  @Autowired private ByodMessageHandler byodMessageHandler;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private ByodRuntimeObservationCoalescer runtimeObservationCoalescer;
  @Autowired private MeterRegistry meterRegistry;

  private KafkaMessageListenerContainer<String, byte[]> listenerContainer;
  private Consumer<String, byte[]> dltConsumer;

  @BeforeEach
  void beforeEach() {
    reset(eventDecoderRegistry, runtimeObservationCoalescer);
    recordingFeatureProducer.reset();

    listenerContainer = byodListenerContainerFactory.create(SOURCE_ID, TOPIC);
    listenerContainer.start();
    awaitAssignment(listenerContainer, 1);

    dltConsumer = createDltConsumer();
  }

  @AfterEach
  void afterEach() {
    if (listenerContainer != null) {
      listenerContainer.stop();
    }
    if (dltConsumer != null) {
      dltConsumer.close();
    }
  }

  @Nested
  class StartupAndWiring {

    @Test
    void createsRecordAckContainerBoundToByodMessageHandler() {
      var container = byodListenerContainerFactory.create(SOURCE_ID, TOPIC);

      try {
        assertEquals(
            ContainerProperties.AckMode.RECORD, container.getContainerProperties().getAckMode());
        assertEquals(
            "processor-byod-" + SOURCE_ID, container.getContainerProperties().getGroupId());
        assertSame(byodMessageHandler, container.getContainerProperties().getMessageListener());
      } finally {
        container.stop();
      }
    }
  }

  @Nested
  class HappyPath {

    @Test
    void consumesByodTopicAndPublishesMappedFeatures() throws Exception {
      var payload = "mapped-payload".getBytes(StandardCharsets.UTF_8);
      var feature = feature("mapped-feature");

      when(eventDecoderRegistry.decodeAndMapRealTimeResult(
              any(UUID.class), any(MappingProperties.class), eq(payload)))
          .thenReturn(result(List.of(feature)));

      kafkaTemplate.send(TOPIC, "key-1", payload);
      kafkaTemplate.flush();

      assertSame(feature, recordingFeatureProducer.awaitPublished());
      verify(eventDecoderRegistry, timeout(5000))
          .decodeAndMapRealTimeResult(
              any(UUID.class), eq(new MappingProperties(SOURCE_ID, null, false)), eq(payload));
      verify(runtimeObservationCoalescer, timeout(5000)).recordSuccess(eq(SOURCE_ID), any(), any());
    }

    @Test
    void structuredOnMissingFailureRecordsSharedMetricsWithoutFeatureOrDlt() throws Exception {
      var payload = "structured-failure".getBytes(StandardCharsets.UTF_8);
      var outcomeTags =
          new String[] {
            "ingress",
            "byod",
            "outcome",
            "failed_all_with_errors",
            "reason",
            "on_missing_error",
            "decoder_kind",
            "dynamic"
          };
      var problemTags =
          new String[] {
            "ingress",
            "byod",
            "resource_kind",
            "data_source",
            "resource_id",
            SOURCE_ID.toString(),
            "outcome",
            "failed_all_with_errors"
          };
      var outcomeBefore =
          counterValue(RealtimeMappingOutcomeObservability.OUTCOME_METRIC, outcomeTags);
      var problemBefore =
          counterValue(RealtimeMappingOutcomeObservability.PROBLEM_METRIC, problemTags);
      when(eventDecoderRegistry.decodeAndMapRealTimeResult(
              any(UUID.class), any(MappingProperties.class), eq(payload)))
          .thenReturn(failedResult());

      kafkaTemplate.send(TOPIC, "key-structured", payload);
      kafkaTemplate.flush();

      verify(runtimeObservationCoalescer, timeout(5000)).recordSuccess(eq(SOURCE_ID), any(), any());
      assertNull(recordingFeatureProducer.pollPublished(Duration.ofMillis(500)));
      assertEquals(
          1.0,
          counterValue(RealtimeMappingOutcomeObservability.OUTCOME_METRIC, outcomeTags)
              - outcomeBefore);
      assertEquals(
          1.0,
          counterValue(RealtimeMappingOutcomeObservability.PROBLEM_METRIC, problemTags)
              - problemBefore);
      assertEquals(0, dltConsumer.poll(Duration.ofMillis(500)).count());
    }
  }

  @Nested
  class RetryOrDltBehavior {

    @Test
    void retriesDecoderFailureThenPublishesOriginalRecordToDlt() throws Exception {
      var payload = "bad-payload".getBytes(StandardCharsets.UTF_8);

      doThrow(new IllegalStateException("boom"))
          .when(eventDecoderRegistry)
          .decodeAndMapRealTimeResult(any(UUID.class), any(MappingProperties.class), eq(payload));

      kafkaTemplate.send(TOPIC, "key-bad", payload);
      kafkaTemplate.flush();

      verify(eventDecoderRegistry, timeout(5000).times(2))
          .decodeAndMapRealTimeResult(
              any(UUID.class), eq(new MappingProperties(SOURCE_ID, null, false)), eq(payload));
      assertNull(recordingFeatureProducer.pollPublished(Duration.ofMillis(500)));

      var dltRecord = awaitSingleRecord(DLT_TOPIC, Duration.ofSeconds(10));
      assertEquals(DLT_TOPIC, dltRecord.topic());
      assertEquals("key-bad", dltRecord.key());
      assertArrayEquals(payload, dltRecord.value());
      verify(runtimeObservationCoalescer, never()).recordSuccess(any(), any(), any());
    }
  }

  private Consumer<String, byte[]> createDltConsumer() {
    var props =
        KafkaTestUtils.consumerProps(
            "processor-byod-component-dlt-" + UUID.randomUUID(), "false", embeddedKafkaBroker);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    var consumer =
        new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer();
    embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
    return consumer;
  }

  private static DecodedMappingResult result(List<Feature> features) {
    return DecodedMappingResult.fromFeatures(UUID.randomUUID(), 1, "test", features);
  }

  private static DecodedMappingResult failedResult() {
    var mapperId = UUID.randomUUID();
    return DecodedMappingResult.fromMappingResponse(
        mapperId,
        1,
        "dynamic",
        new MappingResponse(
            mapperId,
            null,
            List.of(),
            List.of(new MappingError(null, "ON_MISSING_ERROR", "ignored", List.of(), null)),
            List.of()));
  }

  private double counterValue(String name, String... tags) {
    var counter = meterRegistry.find(name).tags(tags).counter();
    return counter == null ? 0 : counter.count();
  }

  private ConsumerRecord<String, byte[]> awaitSingleRecord(String topic, Duration timeout) {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      var records = dltConsumer.poll(Duration.ofMillis(250));
      for (var record : records) {
        if (topic.equals(record.topic())) {
          return record;
        }
      }
    }
    throw new AssertionError("Timed out waiting for Kafka record on topic " + topic);
  }

  private static void awaitAssignment(
      KafkaMessageListenerContainer<String, byte[]> container, int partitions) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> ContainerTestUtils.waitForAssignment(container, partitions),
        "Timed out waiting for BYOD listener container assignment");
  }

  private static Feature feature(String id) {
    var feature = new Feature();
    feature.setId(id);
    return feature;
  }
}
