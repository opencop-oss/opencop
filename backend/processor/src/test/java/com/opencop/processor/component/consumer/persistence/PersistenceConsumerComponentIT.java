// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.persistence;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.technical.model.opencop.Contact;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.component.support.AbstractProcessorKafkaDatabaseIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaDatabaseComponentTestApplication;
import com.opencop.processor.technical.spring.consumer.PersistenceConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    classes = {
      ProcessorKafkaDatabaseComponentTestApplication.class,
      PersistenceConsumerComponentTestContext.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${opencop.messaging-broker.listeners.persist.topic}",
      "${opencop.messaging-broker.listeners.persist.topic}" + DLT_SUFFIX
    })
class PersistenceConsumerComponentIT extends AbstractProcessorKafkaDatabaseIT {

  private static final Duration ASYNC_PERSIST_TIMEOUT = Duration.ofSeconds(20);
  private static final String PERSISTED_FEATURE_SELECT =
      "SELECT id, object_id, source, author_id, created_at, start_at, geom, payload::text AS payload FROM geo.features";

  private static final RowMapper<PersistedFeatureRow> PERSISTED_FEATURE_ROW_MAPPER =
      (resultSet, _) ->
          new PersistedFeatureRow(
              resultSet.getObject("id", UUID.class),
              resultSet.getString("object_id"),
              resultSet.getObject("source", UUID.class),
              resultSet.getObject("author_id", UUID.class),
              resultSet.getTimestamp("created_at").toInstant(),
              resultSet.getTimestamp("start_at").toInstant(),
              resultSet.getString("geom"),
              resultSet.getString("payload"));

  @Autowired private KafkaTemplate<String, Feature> featureKafkaTemplate;
  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private PersistenceConsumer persistenceConsumer;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Value("${opencop.messaging-broker.listeners.persist.topic}")
  private String topic;

  @AfterEach
  void afterEach() {
    jdbcTemplate.execute("TRUNCATE TABLE geo.features CASCADE");
  }

  @Nested
  class StartupAndWiring {

    @Test
    void loadsPersistenceConsumerWithKafkaListenerWiring() {
      assertNotNull(persistenceConsumer);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void deserializesFeaturePayloadAndDelegatesToUseCase() {
      var sourceId = UUID.randomUUID();
      var authorId = UUID.randomUUID();
      var objectId = "persisted-feature-" + UUID.randomUUID();
      seedAuthor(authorId);
      var feature = feature(objectId, sourceId, authorId);

      featureKafkaTemplate.send(topic, "key-" + UUID.randomUUID(), feature);
      featureKafkaTemplate.flush();

      var persisted = awaitPersistedFeature(objectId);
      assertPersistedFeature(persisted, objectId, sourceId, authorId);
    }

    @Test
    void persistsWhenStartAtIsMissing() {
      var sourceId = UUID.randomUUID();
      var authorId = UUID.randomUUID();
      var objectId = "persisted-feature-missing-start-" + UUID.randomUUID();
      seedAuthor(authorId);
      var feature = featureWithMissingStartAt(objectId, sourceId, authorId);
      var recordKey = "key-missing-start-" + UUID.randomUUID();

      featureKafkaTemplate.send(topic, recordKey, feature);
      featureKafkaTemplate.flush();

      var persisted = awaitPersistedFeature(objectId);
      assertPersistedFeature(persisted, objectId, sourceId, authorId);
    }
  }

  @Nested
  class HeadersAndPayloadFailures {

    @Test
    void publishesMalformedPayloadToDltBeforePersistence() {
      var dltKey = "key-bad-" + UUID.randomUUID();
      var malformedPayload = ("{not-json-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

      kafkaTemplate.send(topic, dltKey, malformedPayload);
      kafkaTemplate.flush();

      ConsumerRecord<String, byte[]> dltRecord =
          awaitDltRecord("processor-persist-bad-payload", dltKey);
      assertEquals(dltKey, dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertNull(dltRecord.value());
    }
  }

  @Nested
  class MixedBatchIsolation {

    @Test
    void deadLettersOnlyInvalidRecordAndPersistsPreparedNeighbors() {
      var firstSourceId = UUID.randomUUID();
      var firstAuthorId = UUID.randomUUID();
      seedAuthor(firstAuthorId);

      var secondSourceId = UUID.randomUUID();
      var secondAuthorId = UUID.randomUUID();
      seedAuthor(secondAuthorId);

      var firstFeatureId = "persisted-good-1-" + UUID.randomUUID();
      var secondFeatureId = "persisted-good-2-" + UUID.randomUUID();
      var invalidFeatureId = "invalid-middle-" + UUID.randomUUID();
      var badKey = "key-bad-middle-" + UUID.randomUUID();

      var firstFeature = feature(firstFeatureId, firstSourceId, firstAuthorId);
      var invalidFeature = new Feature();
      invalidFeature.setId(invalidFeatureId);
      invalidFeature.setGeometry(new Point(-75.1652, 39.9526));
      var secondFeature = feature(secondFeatureId, secondSourceId, secondAuthorId);

      featureKafkaTemplate.send(topic, "key-good-1-" + UUID.randomUUID(), firstFeature);
      featureKafkaTemplate.send(topic, badKey, invalidFeature);
      featureKafkaTemplate.send(topic, "key-good-2-" + UUID.randomUUID(), secondFeature);
      featureKafkaTemplate.flush();

      var persistedRows = awaitPersistedFeatures(Set.of(firstFeatureId, secondFeatureId));
      var persistedByObjectId =
          persistedRows.stream()
              .collect(Collectors.toMap(PersistedFeatureRow::objectId, row -> row));
      assertEquals(Set.of(firstFeatureId, secondFeatureId), persistedByObjectId.keySet());
      assertPersistedFeature(
          persistedByObjectId.get(firstFeatureId), firstFeatureId, firstSourceId, firstAuthorId);
      assertPersistedFeature(
          persistedByObjectId.get(secondFeatureId),
          secondFeatureId,
          secondSourceId,
          secondAuthorId);

      var dltRecord = awaitDltRecord("processor-persist-mixed-batch", badKey);
      assertEquals(badKey, dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertTrue(
          KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-exception-message")
              .contains("Bad Request"));
      assertEquals(0, persistedRowCountByPayloadFeatureId(invalidFeatureId));
    }

    @Test
    void deadLettersRecordMissingDbRequiredObjectIdAndPersistsPreparedNeighbors() {
      var firstSourceId = UUID.randomUUID();
      var firstAuthorId = UUID.randomUUID();
      seedAuthor(firstAuthorId);

      var secondSourceId = UUID.randomUUID();
      var secondAuthorId = UUID.randomUUID();
      seedAuthor(secondAuthorId);

      var firstFeatureId = "persisted-good-required-fields-1-" + UUID.randomUUID();
      var secondFeatureId = "persisted-good-required-fields-2-" + UUID.randomUUID();
      var invalidEventId = UUID.randomUUID();
      var badKey = "key-bad-required-object-id-" + UUID.randomUUID();

      var firstFeature = feature(firstFeatureId, firstSourceId, firstAuthorId);
      var invalidFeature =
          featureWithoutObjectId("invalid-required-object-id-" + UUID.randomUUID(), invalidEventId);
      var secondFeature = feature(secondFeatureId, secondSourceId, secondAuthorId);

      featureKafkaTemplate.send(topic, "key-good-required-1-" + UUID.randomUUID(), firstFeature);
      featureKafkaTemplate.send(topic, badKey, invalidFeature);
      featureKafkaTemplate.send(topic, "key-good-required-2-" + UUID.randomUUID(), secondFeature);
      featureKafkaTemplate.flush();

      var persistedRows = awaitPersistedFeatures(Set.of(firstFeatureId, secondFeatureId));
      var persistedByObjectId =
          persistedRows.stream()
              .collect(Collectors.toMap(PersistedFeatureRow::objectId, row -> row));
      assertEquals(Set.of(firstFeatureId, secondFeatureId), persistedByObjectId.keySet());
      assertPersistedFeature(
          persistedByObjectId.get(firstFeatureId), firstFeatureId, firstSourceId, firstAuthorId);
      assertPersistedFeature(
          persistedByObjectId.get(secondFeatureId),
          secondFeatureId,
          secondSourceId,
          secondAuthorId);

      var dltRecord = awaitDltRecord("processor-persist-required-fields", badKey);
      assertEquals(badKey, dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertTrue(
          KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-exception-message")
              .contains("Bad Request"));
      assertEquals(0, persistedRowCountByEventId(invalidEventId));
    }
  }

  @Nested
  class RetryOrDltBehavior {

    @Test
    void publishesPreparationFailureToDltAndDoesNotPersist() {
      var feature = new Feature();
      var invalidFeatureId = "feature-for-dlt-" + UUID.randomUUID();
      feature.setId(invalidFeatureId);
      feature.setGeometry(new Point(-75.1652, 39.9526));
      var dltKey = "key-dlt-" + UUID.randomUUID();

      featureKafkaTemplate.send(topic, dltKey, feature);
      featureKafkaTemplate.flush();

      ConsumerRecord<String, byte[]> dltRecord =
          awaitDltRecord("processor-persist-flow-failure", dltKey);
      assertEquals(dltKey, dltRecord.key());
      assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
      assertTrue(
          KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-exception-message")
              .contains("Bad Request"));
      assertEquals(0, persistedRowCountByPayloadFeatureId(invalidFeatureId));
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
      throw new AssertionError("Timed out waiting for DLT record with key " + expectedKey);
    } finally {
      consumer.close();
    }
  }

  private PersistedFeatureRow awaitPersistedFeature(String expectedObjectId) {
    return awaitPersistedFeatures(Set.of(expectedObjectId)).getFirst();
  }

  private List<PersistedFeatureRow> awaitPersistedFeatures(Set<String> expectedObjectIds) {
    var placeholders =
        String.join(", ", java.util.Collections.nCopies(expectedObjectIds.size(), "?"));
    var query = PERSISTED_FEATURE_SELECT + " WHERE object_id IN (" + placeholders + ")";
    long deadlineNanos = System.nanoTime() + ASYNC_PERSIST_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      var rows =
          jdbcTemplate.query(query, PERSISTED_FEATURE_ROW_MAPPER, expectedObjectIds.toArray());
      if (rows.size() > expectedObjectIds.size()) {
        throw new AssertionError(
            "Expected exactly one persisted row per object id "
                + expectedObjectIds
                + ", observed "
                + rows);
      }
      if (rows.size() == expectedObjectIds.size()) {
        var observedObjectIds =
            rows.stream().map(PersistedFeatureRow::objectId).collect(Collectors.toSet());
        if (!observedObjectIds.equals(expectedObjectIds)) {
          throw new AssertionError(
              "Expected exactly one persisted row per object id "
                  + expectedObjectIds
                  + ", observed "
                  + rows);
        }
        return rows;
      }
      sleepBriefly();
    }
    throw new AssertionError(
        "Timed out waiting for persisted features with object ids " + expectedObjectIds);
  }

  private int persistedRowCountByEventId(UUID eventId) {
    return persistedRowCount("id = ?", eventId);
  }

  private int persistedRowCountByPayloadFeatureId(String featureId) {
    return persistedRowCount("payload ->> 'id' = ?", featureId);
  }

  private int persistedRowCount(String predicate, Object parameter) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM geo.features WHERE " + predicate, Integer.class, parameter);
    return count != null ? count : 0;
  }

  private void assertPersistedFeature(
      PersistedFeatureRow persisted, String objectId, UUID sourceId, UUID authorId) {
    assertEquals(UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)), persisted.id());
    assertEquals(objectId, persisted.objectId());
    assertEquals(sourceId, persisted.source());
    assertEquals(authorId, persisted.authorId());
    assertNotNull(persisted.createdAt());
    assertNotNull(persisted.startAt());
    assertTrue(persisted.geom().contains("Point"));
    assertTrue(persisted.payload().contains(objectId));
  }

  private void sleepBriefly() {
    try {
      Thread.sleep(20);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for database state", e);
    }
  }

  private void seedAuthor(UUID authorId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        authorId,
        "idp-" + authorId,
        "user-" + authorId,
        "https://issuer.test",
        "user-" + authorId,
        "user-" + authorId + "@example.com",
        "Test",
        "Author",
        "Test Author");
  }

  private static Feature feature(String objectId, UUID sourceId, UUID authorId) {
    return feature(
        objectId,
        UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)),
        objectId,
        sourceId,
        authorId);
  }

  private static Feature featureWithoutObjectId(String featureId, UUID opencopId) {
    return feature(featureId, opencopId, null, UUID.randomUUID(), UUID.randomUUID());
  }

  private static Feature feature(
      String featureId, UUID opencopId, String objectId, UUID sourceId, UUID authorId) {
    long now = System.currentTimeMillis();
    var properties =
        new OpenCopProperties.Builder()
            .id(opencopId)
            .objectId(objectId)
            .kind("track")
            .source(sourceId)
            .displayName(featureId)
            .author(new Contact.Builder().withUserId(authorId).build())
            .timestampz(new Timestampz(now, now, now + 60_000))
            .build();

    Feature feature =
        OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
    feature.setId(objectId);
    return feature;
  }

  private static Feature featureWithMissingStartAt(String objectId, UUID sourceId, UUID authorId) {
    long now = System.currentTimeMillis();
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)))
            .objectId(objectId)
            .kind("track")
            .source(sourceId)
            .displayName(objectId)
            .author(
                new com.opencop.core.sdk.technical.model.opencop.Contact.Builder()
                    .withUserId(authorId)
                    .build())
            .timestampz(new Timestampz(now, 0, now + 60_000))
            .build();

    Feature feature =
        OpenCopPropertiesParser.writeToFeature(properties, new Point(-75.1652, 39.9526));
    feature.setId(objectId);
    return feature;
  }

  private record PersistedFeatureRow(
      UUID id,
      String objectId,
      UUID source,
      UUID authorId,
      java.time.Instant createdAt,
      java.time.Instant startAt,
      String geom,
      String payload) {}
}
