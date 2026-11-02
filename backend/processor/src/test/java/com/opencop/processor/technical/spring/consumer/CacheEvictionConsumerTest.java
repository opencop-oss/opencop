// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonSerializer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class CacheEvictionConsumerTest {

  private static final String DLT_TOPIC = "opencop.geo.validated.persist.dlt";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final String OBJECT_ID = "550e8400-e29b-41d4-a716-446655440002";
  private static final long EVENT_TIME_MS = 1_700_000_000_000L;

  private final KafkaGeoJsonSerializer serializer = new KafkaGeoJsonSerializer();

  private GeoCacheGateway cacheGateway;
  private CacheEvictionConsumer consumer;

  @BeforeEach
  void setUp() {
    cacheGateway = mock(GeoCacheGateway.class);
    consumer = new CacheEvictionConsumer(cacheGateway);
  }

  @Test
  void evictFromDlt_whenPersistFailure_evictsObjectUsingEventTimestamp() {
    var record = dltRecord("some postgres error", payload(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS));

    consumer.evictFromDlt(record);

    verify(cacheGateway).evictObject(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS);
  }

  @Test
  void evictFromDlt_whenNoTimestampz_skipsEviction() {
    var record = dltRecord("some postgres error", payloadNoTimestamp(SOURCE_ID, OBJECT_ID));

    consumer.evictFromDlt(record);

    verify(cacheGateway, never()).evictObject(any(), any(), anyLong());
  }

  @Test
  void evictFromDlt_whenTrueDuplicate_doesNotEvict() {
    var record =
        dltRecord(
            "ON CONFLICT DO UPDATE command cannot affect row a second time",
            payload(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS));

    consumer.evictFromDlt(record);

    verify(cacheGateway, never()).evictObject(any(), any(), anyLong());
  }

  @Test
  void evictFromDlt_whenNonPersistStage_doesNotEvict() {
    var record =
        new ConsumerRecord<String, byte[]>(
            DLT_TOPIC, 0, 0L, null, payload(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS));
    header(record, DeadLetterHeaders.STAGE, "REAL_TIME");

    consumer.evictFromDlt(record);

    verify(cacheGateway, never()).evictObject(any(), any(), anyLong());
  }

  @Test
  void evictFromDlt_whenNullValue_doesNotEvict() {
    var record = new ConsumerRecord<String, byte[]>(DLT_TOPIC, 0, 0L, null, null);
    header(record, DeadLetterHeaders.STAGE, "PERSIST");

    consumer.evictFromDlt(record);

    verify(cacheGateway, never()).evictObject(any(), any(), anyLong());
  }

  @Test
  void evictFromDlt_whenEvictionThrows_doesNotPropagate() {
    doThrow(new RuntimeException("redis down"))
        .when(cacheGateway)
        .evictObject(any(), any(), anyLong());
    var record = dltRecord("some error", payload(SOURCE_ID, OBJECT_ID, EVENT_TIME_MS));

    // must not throw — rethrow would cause Kafka to retry the DLT record indefinitely
    consumer.evictFromDlt(record);
  }

  // --- Helpers ---

  private ConsumerRecord<String, byte[]> dltRecord(String exceptionMessage, byte[] value) {
    var record = new ConsumerRecord<String, byte[]>(DLT_TOPIC, 0, 0L, null, value);
    header(record, DeadLetterHeaders.STAGE, "PERSIST");
    header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, exceptionMessage);
    return record;
  }

  private static void header(ConsumerRecord<?, ?> record, String name, String value) {
    record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
  }

  private byte[] payload(UUID sourceId, String objectId, long eventTimeMs) {
    var properties = new OpenCopProperties();
    properties.setSource(sourceId);
    properties.setObjectId(objectId);
    var timestampz = new Timestampz.Builder().time(eventTimeMs).build();
    properties.setTimestampz(timestampz);
    var feature = OpenCopPropertiesParser.writeToFeature(properties, null);
    return serializer.serialize(DLT_TOPIC, feature);
  }

  private byte[] payloadNoTimestamp(UUID sourceId, String objectId) {
    var properties = new OpenCopProperties();
    properties.setSource(sourceId);
    properties.setObjectId(objectId);
    var feature = OpenCopPropertiesParser.writeToFeature(properties, null);
    return serializer.serialize(DLT_TOPIC, feature);
  }
}
