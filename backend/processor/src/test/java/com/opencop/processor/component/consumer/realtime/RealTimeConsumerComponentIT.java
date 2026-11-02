// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.realtime;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.elements.ElementTypes;
import com.opencop.core.sdk.technical.model.opencop.elements.PointElement;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.processor.business.usecase.event.RealTimeGeoFeatureHandler;
import com.opencop.processor.component.support.AbstractProcessorKafkaComponentIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      RealTimeConsumerComponentTestContext.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${opencop.messaging-broker.listeners.realTime.topic}",
      "${opencop.messaging-broker.listeners.realTime.topic}" + DLT_SUFFIX
    })
class RealTimeConsumerComponentIT extends AbstractProcessorKafkaComponentIT {
  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration QUIET_PERIOD = Duration.ofMillis(500);

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private RealTimeEventHandler<List<Feature>> realTimeEventHandler;
  @Autowired private RedisEvalProbe redisEvalProbe;
  @Autowired private RecordingCacheEventsPublisher cacheEventsPublisher;
  @Autowired private Serializer<Feature, byte[]> featureSerializer;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Value("${opencop.messaging-broker.listeners.realTime.topic}")
  private String topic;

  @BeforeEach
  void beforeEach() {
    MessageListenerContainer listenerContainer =
        kafkaListenerEndpointRegistry.getListenerContainer("realTimeConsumer");
    assertNotNull(listenerContainer);
    ContainerTestUtils.waitForAssignment(
        listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
  }

  @AfterEach
  void afterEach() {
    cacheEventsPublisher.reset();
    redisEvalProbe.reset();
  }

  @Nested
  class StartupAndWiring {

    @Test
    void wiresRealTimeConsumerToRealHandler() {
      assertInstanceOf(RealTimeGeoFeatureHandler.class, realTimeEventHandler);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void consumesValidatedFeaturesAndWritesExpectedPayloadsToRedis() throws Exception {
      var sourceId = UUID.fromString("3f45e6b4-bf74-4ee4-bf8f-86233b0fe2cf");
      var first = feature("event-1", sourceId, 1000L, 2000L, 7000L, -75.1652, 39.9526);
      var second = feature("event-2", sourceId, 1100L, 2100L, 7100L, -75.18, 39.94);

      featureKafkaTemplate.send(topic, "key-1", first);
      featureKafkaTemplate.send(topic, "key-2", second);
      featureKafkaTemplate.flush();

      var redisCalls = redisEvalProbe.awaitExactEvalCount(2, ASYNC_TIMEOUT, QUIET_PERIOD);
      assertEquals(1, redisEvalProbe.scriptLoadCalls());
      assertRedisInvocation(sourceId, "event-1", 2000L, 7000L, first, redisCalls.get(0));
      assertRedisInvocation(sourceId, "event-2", 2100L, 7100L, second, redisCalls.get(1));

      assertEquals(
          List.of(new CacheEventsNotification(sourceId, List.of("event-1", "event-2"))),
          cacheEventsPublisher.awaitExactPublishedCount(1, ASYNC_TIMEOUT, QUIET_PERIOD));
    }
  }

  @Nested
  class OtherEdgeCases {

    @Test
    void skipsRedisForMissingOrUnserializableFeaturesButPublishesUpdatesForParseableOnes()
        throws Exception {
      var sourceId = UUID.fromString("ef0d1852-a89f-4e9d-b7af-1cb18fa83d9d");
      var valid = feature("valid-event", sourceId, 1000L, 2000L, 8000L, -75.15, 39.95);
      var missingOpencop = new Feature();
      missingOpencop.setGeometry(new Point(-75.0, 39.0));
      var unserializable =
          featureWithoutGeometry("unserializable-event", sourceId, 1200L, 2200L, 8200L);

      featureKafkaTemplate.send(topic, "key-1", valid);
      featureKafkaTemplate.send(topic, "key-2", missingOpencop);
      featureKafkaTemplate.send(topic, "key-3", unserializable);
      featureKafkaTemplate.flush();

      var redisCalls = redisEvalProbe.awaitExactEvalCount(1, ASYNC_TIMEOUT, QUIET_PERIOD);
      assertRedisInvocation(sourceId, "valid-event", 2000L, 8000L, valid, redisCalls.getFirst());

      assertEquals(
          List.of(
              new CacheEventsNotification(
                  sourceId, List.of("valid-event", "unserializable-event"))),
          cacheEventsPublisher.awaitExactPublishedCount(1, ASYNC_TIMEOUT, QUIET_PERIOD));
    }

    @Test
    void publishesOneNotificationPerSourceAndOnlyWritesParseableSerializableEventsToRedis()
        throws Exception {
      var sourceOne = UUID.fromString("673067c6-c0ab-44e7-a308-6f3b09410cba");
      var sourceTwo = UUID.fromString("22c1323d-77c4-4a26-96e0-f29657993310");
      var first = feature("source-1-event-1", sourceOne, 1000L, 2000L, 8000L, -75.15, 39.95);
      var second = feature("source-1-event-2", sourceOne, 1100L, 2100L, 8100L, -75.14, 39.96);
      var third = feature("source-2-event-1", sourceTwo, 1200L, 2200L, 8200L, -75.13, 39.97);
      var missingOpencop = new Feature();
      missingOpencop.setGeometry(new Point(-75.0, 39.0));
      var unserializable =
          featureWithoutGeometry("source-2-event-2", sourceTwo, 1300L, 2300L, 8300L);

      featureKafkaTemplate.send(topic, "key-1", first);
      featureKafkaTemplate.send(topic, "key-2", second);
      featureKafkaTemplate.send(topic, "key-3", third);
      featureKafkaTemplate.send(topic, "key-4", missingOpencop);
      featureKafkaTemplate.send(topic, "key-5", unserializable);
      featureKafkaTemplate.flush();

      var redisCalls = redisEvalProbe.awaitExactEvalCount(3, ASYNC_TIMEOUT, QUIET_PERIOD);
      assertRedisInvocation(sourceOne, "source-1-event-1", 2000L, 8000L, first, redisCalls.get(0));
      assertRedisInvocation(sourceOne, "source-1-event-2", 2100L, 8100L, second, redisCalls.get(1));
      assertRedisInvocation(sourceTwo, "source-2-event-1", 2200L, 8200L, third, redisCalls.get(2));

      assertEquals(
          Set.of(
              new CacheEventsNotification(
                  sourceOne, List.of("source-1-event-1", "source-1-event-2")),
              new CacheEventsNotification(
                  sourceTwo, List.of("source-2-event-1", "source-2-event-2"))),
          Set.copyOf(
              cacheEventsPublisher.awaitExactPublishedCount(2, ASYNC_TIMEOUT, QUIET_PERIOD)));
    }
  }

  @Nested
  class RetryOrDltBehavior {

    @Test
    void consume_malformedOpenCopProperties_routesToDltWithoutRedisOrNotification() {
      var malformed = new Feature();
      malformed.setGeometry(new Point(-75.0, 39.0));
      malformed.setProperty("opencop", "not-an-object");
      var dltKey = "real-time-malformed-" + UUID.randomUUID();

      featureKafkaTemplate.send(topic, dltKey, malformed);
      featureKafkaTemplate.flush();

      var dltRecord = awaitDltRecord("processor-real-time-malformed", dltKey);
      assertEquals(dltKey, dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertEquals(List.of(), redisEvalProbe.evalInvocations());
      assertEquals(List.of(), cacheEventsPublisher.published());
    }
  }

  private void assertRedisInvocation(
      UUID sourceId,
      String objectId,
      long expectedEventTime,
      long expectedStaleAt,
      Feature feature,
      RedisEvalProbe.EvalInvocation invocation) {
    assertEquals("sha-real-time", invocation.scriptSha());
    assertEquals(4, invocation.keyCount());

    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(sourceId);
    var arguments = invocation.arguments();
    assertEquals(12, arguments.length);
    assertEquals(sourceKeys.eventKey(objectId), s(arguments[0]));
    assertEquals(sourceKeys.updatedAtKey(), s(arguments[1]));
    assertEquals(sourceKeys.staleAtKey(), s(arguments[2]));
    assertEquals(sourceKeys.registryKey(), s(arguments[3]));
    assertEquals(bytes(featureSerializer.serialize(feature)), bytes(arguments[4]));
    assertEquals(expectedEventTime, Long.parseLong(s(arguments[6])));
    assertEquals(expectedStaleAt, Long.parseLong(s(arguments[7])));
    var nowMs = Long.parseLong(s(arguments[8]));
    assertEquals(expectedStaleAt - nowMs, Long.parseLong(s(arguments[5])));
    assertEquals(sourceKeys.sourcePrefix(), s(arguments[9]));
    assertEquals(60_000L, Long.parseLong(s(arguments[10])));
    assertEquals(sourceKeys.registryKind().wireValue(), s(arguments[11]));
  }

  private static String s(byte[] value) {
    return new String(value, UTF_8);
  }

  private static List<Byte> bytes(byte[] value) {
    return java.util.stream.IntStream.range(0, value.length).mapToObj(i -> value[i]).toList();
  }

  private static Feature feature(
      String objectId,
      UUID sourceId,
      long start,
      long time,
      long stale,
      double longitude,
      double latitude) {
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(objectId.getBytes(UTF_8)))
            .objectId(objectId)
            .source(sourceId)
            .displayName(objectId)
            .timestampz(new Timestampz(time, start, stale))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build();

    return OpenCopPropertiesParser.writeToFeature(properties, new Point(longitude, latitude));
  }

  private static Feature featureWithoutGeometry(
      String objectId, UUID sourceId, long start, long time, long stale) {
    var feature = new Feature();
    feature.setProperty(
        "opencop",
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(objectId.getBytes(UTF_8)))
            .objectId(objectId)
            .source(sourceId)
            .displayName(objectId)
            .timestampz(new Timestampz(time, start, stale))
            .element(new PointElement.Builder().type(ElementTypes.POINT).build())
            .build());
    return feature;
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
      long deadlineNanos = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
      while (System.nanoTime() < deadlineNanos) {
        var records = consumer.poll(Duration.ofMillis(250));
        for (var record : records) {
          if (expectedKey.equals(record.key())) {
            return record;
          }
        }
      }
      throw new AssertionError("Timed out waiting for DLT record with key " + expectedKey);
    } finally {
      consumer.close();
    }
  }
}
