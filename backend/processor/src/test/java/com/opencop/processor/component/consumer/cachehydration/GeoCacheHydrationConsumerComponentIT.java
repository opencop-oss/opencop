// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.cachehydration;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.component.support.AbstractProcessorKafkaDatabaseIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.ports.business.usecase.event.GeoCacheHydrator;
import com.opencop.processor.technical.spring.consumer.GeoCacheHydrationConsumer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.geojson.Point;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {GeoCacheHydrationConsumerComponentTestApplication.class})
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${opencop.messaging-broker.listeners.jobs.topic}",
      "${opencop.messaging-broker.listeners.jobs.topic}" + DLT_SUFFIX
    })
class GeoCacheHydrationConsumerComponentIT extends AbstractProcessorKafkaDatabaseIT {

  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration QUIET_PERIOD = Duration.ofMillis(500);

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private GeoCacheHydrator geoCacheHydrator;
  @Autowired private GeoCacheHydrationConsumer geoCacheHydrationConsumer;
  @Autowired private RecordingGeoCacheGateway recordingGeoCacheGateway;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
  @Autowired private ObjectMapper objectMapper;

  @Value("${opencop.messaging-broker.listeners.jobs.topic}")
  private String topic;

  @AfterEach
  void afterEach() {
    recordingGeoCacheGateway.reset();
  }

  @Nested
  class StartupAndWiring {

    @Test
    void wiresKafkaConsumerToHydrationFlow() {
      assertNotNull(geoCacheHydrationConsumer);
      assertNotNull(geoCacheHydrator);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void hydratesLatestActiveFeaturesForSourceFromDatabase() throws Exception {
      var sourceId = UUID.fromString("b7204508-94b6-47a3-a2dd-0a423823ef0f");
      var otherSourceId = UUID.fromString("971f4a42-b7b2-466f-92cb-7216197d1d8d");
      var now = Instant.now();

      persistFeatureRow(
          feature("alpha", sourceId, 1_000L),
          sourceId,
          "alpha",
          now.minusSeconds(120),
          now.plusSeconds(300));
      persistFeatureRow(
          feature("alpha", sourceId, 2_000L),
          sourceId,
          "alpha",
          now.minusSeconds(60),
          now.plusSeconds(300));
      persistFeatureRow(
          feature("bravo", sourceId, 3_000L),
          sourceId,
          "bravo",
          now.minusSeconds(30),
          now.plusSeconds(300));
      persistFeatureRow(
          feature("charlie", sourceId, 4_000L),
          sourceId,
          "charlie",
          now.minusSeconds(10),
          now.minusSeconds(1));
      persistFeatureRow(
          feature("delta", otherSourceId, 5_000L),
          otherSourceId,
          "delta",
          now.minusSeconds(5),
          now.plusSeconds(300));

      var job =
          GeoCacheHydrationJobSubmission.forSource(
              JobIssuer.system("component-test-hydrator-1"),
              sourceId,
              GeoCacheHydrationJob.Trigger.MANUAL);
      kafkaTemplate.send(topic, sourceId.toString(), objectMapper.writeValueAsBytes(job));
      kafkaTemplate.flush();

      var writes = recordingGeoCacheGateway.awaitExactWriteCount(1, ASYNC_TIMEOUT, QUIET_PERIOD);
      var invocation = writes.getFirst();
      assertTrue(invocation.publishUpdates());
      assertEquals(List.of("alpha", "bravo"), objectIds(invocation.features()));
      assertEquals(List.of(2_000L, 3_000L), eventTimes(invocation.features()));
    }

    @Test
    void hydratesLatestActiveFeaturesUsingStartAtNotInsertionOrder() throws Exception {
      var sourceId = UUID.fromString("3cfc3667-7f2d-4be9-bb8a-1f4f5d4dc0c8");
      var now = Instant.now();

      persistFeatureRow(
          feature("alpha", sourceId, 2_000L),
          sourceId,
          "alpha",
          now.minusSeconds(10),
          Instant.ofEpochMilli(2_000L),
          now.plusSeconds(300));
      persistFeatureRow(
          feature("alpha", sourceId, 1_000L),
          sourceId,
          "alpha",
          now.minusSeconds(5),
          Instant.ofEpochMilli(1_000L),
          now.plusSeconds(300));

      var job =
          GeoCacheHydrationJobSubmission.forSource(
              JobIssuer.system("component-test-hydrator-2"),
              sourceId,
              GeoCacheHydrationJob.Trigger.MANUAL);
      kafkaTemplate.send(topic, sourceId.toString(), objectMapper.writeValueAsBytes(job));
      kafkaTemplate.flush();

      var writes = recordingGeoCacheGateway.awaitExactWriteCount(1, ASYNC_TIMEOUT, QUIET_PERIOD);
      var invocation = writes.getFirst();
      assertEquals(List.of("alpha"), objectIds(invocation.features()));
      assertEquals(List.of(2_000L), eventTimes(invocation.features()));
    }
  }

  @Nested
  class HeadersAndPayloadFailures {

    @Test
    void routesMalformedHydrationPayloadToDlt() {
      kafkaTemplate.send(topic, "bad-key", "{".getBytes(UTF_8));
      kafkaTemplate.flush();

      ConsumerRecord<String, byte[]> dltRecord =
          singleDltRecord("processor-cache-hydration-bad-payload");
      assertEquals("bad-key", dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertTrue(recordingGeoCacheGateway.awaitWriteCountEventually(0, Duration.ofSeconds(2)));
    }
  }

  private ConsumerRecord<String, byte[]> singleDltRecord(String groupId) {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer();
    try {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic + DLT_SUFFIX);
      return KafkaTestUtils.getSingleRecord(consumer, topic + DLT_SUFFIX, Duration.ofSeconds(10));
    } finally {
      consumer.close();
    }
  }

  private void persistFeatureRow(
      Feature feature, UUID sourceId, String objectId, Instant createdAt, Instant staleAt)
      throws Exception {
    persistFeatureRow(feature, sourceId, objectId, createdAt, createdAt, staleAt);
  }

  private void persistFeatureRow(
      Feature feature,
      UUID sourceId,
      String objectId,
      Instant createdAt,
      Instant startAt,
      Instant staleAt)
      throws Exception {
    jdbcTemplate.update(
        """
        INSERT INTO geo.features
          (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
        VALUES
          (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        UUID.randomUUID(),
        "track",
        sourceId,
        objectId,
        java.sql.Timestamp.from(createdAt),
        java.sql.Timestamp.from(startAt),
        java.sql.Timestamp.from(staleAt),
        "{\"type\":\"Point\",\"coordinates\":[-75.1652,39.9526]}",
        objectMapper.writeValueAsString(feature));
  }

  private static Feature feature(String objectId, UUID sourceId, long eventTime) {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(objectId.getBytes(UTF_8)))
            .objectId(objectId)
            .kind("track")
            .source(sourceId)
            .displayName(objectId)
            .timestampz(new Timestampz(eventTime, eventTime - 100, eventTime + 60_000))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build();

    return OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
  }

  private static List<String> objectIds(List<Feature> features) {
    return features.stream()
        .map(OpenCopPropertiesParser::readFromFeature)
        .map(OpenCopProperties::getObjectId)
        .toList();
  }

  private static List<Long> eventTimes(List<Feature> features) {
    return features.stream()
        .map(OpenCopPropertiesParser::readFromFeature)
        .map(props -> props.getTimestampz().getTime())
        .toList();
  }
}
