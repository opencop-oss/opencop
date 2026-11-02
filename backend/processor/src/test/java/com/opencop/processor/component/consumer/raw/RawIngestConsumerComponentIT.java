// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.raw;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OBJECT_ID;
import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.SOURCE_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.processor.business.usecase.event.MapRawToGeoHandler;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.component.support.AbstractProcessorKafkaComponentIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      RawIngestConsumerComponentTestContext.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${opencop.messaging-broker.listeners.raw.topic}",
      "${opencop.messaging-broker.listeners.raw.topic}" + DLT_SUFFIX
    })
class RawIngestConsumerComponentIT extends AbstractProcessorKafkaComponentIT {

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private RawEventHandler<byte[]> rawEventHandler;
  @Autowired private EventDecoderRegistry eventDecoderRegistry;
  @Autowired private RecordingFeatureProducer mappedProducer;
  @Autowired private MessageBrokerProducer<byte[]> rawDltProducer;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private MeterRegistry meterRegistry;

  @Value("${opencop.messaging-broker.listeners.raw.topic}")
  private String topic;

  @AfterEach
  void afterEach() {
    reset(eventDecoderRegistry);
    reset(rawDltProducer);
    mappedProducer.reset();
  }

  @Nested
  class StartupAndWiring {

    @Test
    void wiresRawConsumerToRealMapRawToGeoHandler() {
      assertInstanceOf(MapRawToGeoHandler.class, rawEventHandler);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void decodesAndPublishesMappedFeatures() throws Exception {
      var payload1 = "first-payload".getBytes(StandardCharsets.UTF_8);
      var payload2 = "second-payload".getBytes(StandardCharsets.UTF_8);
      var sourceId = UUID.randomUUID();
      var objectId1 = UUID.randomUUID();
      var objectId2 = UUID.randomUUID();
      var feature1 = feature("feature-1");
      var feature2 = feature("feature-2");

      when(eventDecoderRegistry.decodeAndMapRealTimeResult(eq(objectId1), any(), eq(payload1)))
          .thenReturn(result(List.of(feature1)));
      when(eventDecoderRegistry.decodeAndMapRealTimeResult(eq(objectId2), any(), eq(payload2)))
          .thenReturn(result(List.of(feature2)));

      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic, "key-1", payload1, requiredHeaders(sourceId, objectId1)));
      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic, "key-2", payload2, requiredHeaders(sourceId, objectId2)));
      kafkaTemplate.flush();

      assertSame(feature1, mappedProducer.awaitPublished());
      assertSame(feature2, mappedProducer.awaitPublished());
    }

    @Test
    void structuredOnMissingFailureRecordsSharedMetricsWithoutFeatureOrDlt() throws Exception {
      var payload = "structured-failure".getBytes(StandardCharsets.UTF_8);
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var outcomeTags =
          new String[] {
            "ingress",
            "raw_realtime",
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
            "raw_realtime",
            "resource_kind",
            "data_source",
            "resource_id",
            sourceId.toString(),
            "outcome",
            "failed_all_with_errors"
          };
      var outcomeBefore =
          counterValue(RealtimeMappingOutcomeObservability.OUTCOME_METRIC, outcomeTags);
      var problemBefore =
          counterValue(RealtimeMappingOutcomeObservability.PROBLEM_METRIC, problemTags);
      when(eventDecoderRegistry.decodeAndMapRealTimeResult(eq(objectId), any(), eq(payload)))
          .thenReturn(failedResult());

      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic, "key-structured", payload, requiredHeaders(sourceId, objectId)));
      kafkaTemplate.flush();

      verify(eventDecoderRegistry, org.mockito.Mockito.timeout(5000))
          .decodeAndMapRealTimeResult(eq(objectId), any(), eq(payload));
      assertNull(mappedProducer.pollPublished(Duration.ofMillis(500)));
      verifyNoInteractions(rawDltProducer);
      assertEquals(
          1.0,
          counterValue(RealtimeMappingOutcomeObservability.OUTCOME_METRIC, outcomeTags)
              - outcomeBefore);
      assertEquals(
          1.0,
          counterValue(RealtimeMappingOutcomeObservability.PROBLEM_METRIC, problemTags)
              - problemBefore);
    }
  }

  @Nested
  class HeadersAndPayloadFailures {

    @Test
    void dropsMessagesWithMissingRequiredHeaders() throws Exception {
      var payload = "payload".getBytes(StandardCharsets.UTF_8);

      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic,
              "key-missing-source",
              payload,
              Map.of(OBJECT_ID.getName(), UUID.randomUUID().toString())));
      kafkaTemplate.flush();

      assertNull(mappedProducer.pollPublished(Duration.ofMillis(500)));
      verifyNoInteractions(eventDecoderRegistry);
    }

    @Test
    void doesNotPublishWhenDecoderFails() throws Exception {
      var payload = "bad-payload".getBytes(StandardCharsets.UTF_8);
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();

      doThrow(new IllegalStateException("boom"))
          .when(eventDecoderRegistry)
          .decodeAndMapRealTimeResult(eq(objectId), any(), eq(payload));

      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic, "key-bad-payload", payload, requiredHeaders(sourceId, objectId)));
      kafkaTemplate.flush();

      assertNull(mappedProducer.pollPublished(Duration.ofMillis(500)));
      assertEquals(List.of(), mappedProducer.dltPublished());
    }

    @Test
    void retriesThrownHandlerFailureThenPublishesOriginalRecordToRawDlt() throws Exception {
      var payload = "bad-payload-for-dlt".getBytes(StandardCharsets.UTF_8);
      var sourceId = UUID.randomUUID();
      var objectId = UUID.randomUUID();
      var key = "key-final-dlt-" + UUID.randomUUID();

      doThrow(new IllegalArgumentException("bad raw"))
          .when(eventDecoderRegistry)
          .decodeAndMapRealTimeResult(eq(objectId), any(), eq(payload));
      doThrow(new IllegalStateException("manual raw dlt unavailable"))
          .when(rawDltProducer)
          .sendAll(any(Collection.class));

      kafkaTemplate.send(
          KafkaTestRecords.byteArrayRecord(
              topic, key, payload, requiredHeaders(sourceId, objectId)));
      kafkaTemplate.flush();

      ConsumerRecord<String, byte[]> dltRecord =
          awaitDltRecord("processor-raw-final-dlt-" + UUID.randomUUID(), key);

      verify(eventDecoderRegistry, atLeast(2))
          .decodeAndMapRealTimeResult(eq(objectId), any(), eq(payload));
      verify(rawDltProducer, atLeast(2)).sendAll(any(Collection.class));
      assertEquals(key, dltRecord.key());
      assertArrayEquals(payload, dltRecord.value());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertEquals("0", KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-partition"));
      assertEquals("RAW", KafkaTestRecords.stringHeader(dltRecord, DeadLetterHeaders.STAGE));
      assertEquals(
          "PROCESSING", KafkaTestRecords.stringHeader(dltRecord, DeadLetterHeaders.FAILURE_KIND));
      assertNull(mappedProducer.pollPublished(Duration.ofMillis(500)));
    }
  }

  private ConsumerRecord<String, byte[]> awaitDltRecord(String groupId, String expectedKey) {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer();
    try {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic + DLT_SUFFIX);
      long deadlineNanos = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (System.nanoTime() < deadlineNanos) {
        var records = consumer.poll(Duration.ofMillis(250));
        for (var record : records) {
          if (expectedKey.equals(record.key())) {
            return record;
          }
        }
      }
      throw new AssertionError("Timed out waiting for raw DLT record with key " + expectedKey);
    } finally {
      consumer.close();
    }
  }

  private static Map<String, String> requiredHeaders(UUID sourceId, UUID objectId) {
    return Map.of(
        SOURCE_ID.getName(), sourceId.toString(), OBJECT_ID.getName(), objectId.toString());
  }

  private static Feature feature(String id) {
    var feature = new Feature();
    feature.setId(id);
    return feature;
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
}
