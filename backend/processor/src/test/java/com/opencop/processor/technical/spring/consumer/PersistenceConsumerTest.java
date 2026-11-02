// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.processor.business.usecase.event.EventAutoAttachmentService;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordMapper;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class PersistenceConsumerTest {

  @Test
  void persistPublishesManualDltWithoutDestinationPartitionAndPreservesOriginalPartitionHeader()
      throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var retryingPersistenceService = mock(RetryingPersistenceService.class);
    var kafkaTemplate = mock(KafkaTemplate.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    var sourceId = UUID.randomUUID();
    var importId = UUID.randomUUID();
    var feature = new Feature();
    var properties = new OpenCopProperties();
    properties.setSource(sourceId);
    properties.setImportId(importId);
    feature.setProperty("opencop", properties);
    when(mapper.toRecord(eq(feature), any()))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.BAD_REQUEST));

    var consumer =
        new PersistenceConsumer(
            mapper, retryingPersistenceService, kafkaTemplate, autoAttachmentService);
    var consumerRecord = new ConsumerRecord<>("opencop.geo.validated", 3, 12L, "bad-key", feature);
    consumerRecord
        .headers()
        .add("Authorization", "Bearer should-not-copy".getBytes(StandardCharsets.UTF_8));

    consumer.persist(List.of(consumerRecord));

    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());

    ProducerRecord<String, Feature> dltRecord = recordCaptor.getValue();
    assertEquals("opencop.geo.validated.dlt", dltRecord.topic());
    assertNull(dltRecord.partition());
    assertEquals("3", header(dltRecord, "kafka_dlt-original-partition"));
    assertEquals("opencop.geo.validated", header(dltRecord, "kafka_dlt-original-topic"));
    assertEquals("12", header(dltRecord, "kafka_dlt-original-offset"));
    assertEquals(
        RepositoryException.class.getName(), header(dltRecord, "kafka_dlt-exception-fqcn"));
    assertEquals("Bad Request", header(dltRecord, "kafka_dlt-exception-message"));
    assertEquals("PERSIST", header(dltRecord, DeadLetterHeaders.STAGE));
    assertEquals("MALFORMED", header(dltRecord, DeadLetterHeaders.FAILURE_KIND));
    assertEquals(
        """
        {"sourceId":"%s","importId":"%s"}
        """
            .formatted(sourceId, importId)
            .trim(),
        header(dltRecord, DeadLetterFailureIndexer.CONTEXT_HEADER));
    assertNull(header(dltRecord, "Authorization"));
    verify(retryingPersistenceService, never()).persist(any(), any());
    verify(autoAttachmentService, never()).attachFromRecord(any(), any());
  }

  @Test
  void persistDelegatesPreparedRecordsToRetryingPersistenceService() throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var retryingPersistenceService = mock(RetryingPersistenceService.class);
    var kafkaTemplate = mock(KafkaTemplate.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    var feature = new Feature();
    var postGisFeatureRecord =
        new PostGisFeatureRecord(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440700"),
            "TRACK",
            UUID.fromString("550e8400-e29b-41d4-a716-446655440701"),
            null,
            "obj-1",
            null,
            null,
            "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
            "{\"type\":\"Feature\"}");
    when(mapper.toRecord(eq(feature), any())).thenReturn(postGisFeatureRecord);

    var consumer =
        new PersistenceConsumer(
            mapper, retryingPersistenceService, kafkaTemplate, autoAttachmentService);

    consumer.persist(List.of(new ConsumerRecord<>("opencop.geo.validated", 0, 1L, "key", feature)));

    verify(retryingPersistenceService).persist(eq(List.of(postGisFeatureRecord)), any());
  }

  @Test
  void persistCallsAutoAttachWhenSourceIsRtsp() throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var retryingPersistenceService = mock(RetryingPersistenceService.class);
    var kafkaTemplate = mock(KafkaTemplate.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440701");
    var feature = new Feature();
    var postGisFeatureRecord =
        new PostGisFeatureRecord(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440700"),
            "TRACK",
            sourceId,
            null,
            "obj-1",
            null,
            null,
            "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
            "{\"type\":\"Feature\"}");
    when(mapper.toRecord(eq(feature), any())).thenReturn(postGisFeatureRecord);

    var consumer =
        new PersistenceConsumer(
            mapper, retryingPersistenceService, kafkaTemplate, autoAttachmentService);

    var consumerRecord =
        new ConsumerRecord<String, Feature>("opencop.geo.validated", 0, 1L, "key", feature);
    consumer.persist(List.of(consumerRecord));

    verify(autoAttachmentService).attachFromRecord(eq(sourceId), eq("obj-1"));
  }

  @Test
  void persist_whenAttachmentFails_continuesRemainingRecords() throws Exception {
    var mapper = mock(PostGisFeatureRecordMapper.class);
    var autoAttachmentService = mock(EventAutoAttachmentService.class);
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440721");
    var first = new Feature();
    first.setId("first");
    var second = new Feature();
    second.setId("second");
    when(mapper.toRecord(eq(first), any())).thenReturn(record(sourceId, "obj-1"));
    when(mapper.toRecord(eq(second), any())).thenReturn(record(sourceId, "obj-2"));
    doThrow(new RuntimeException("DB down"))
        .when(autoAttachmentService)
        .attachFromRecord(sourceId, "obj-1");
    var consumer =
        new PersistenceConsumer(
            mapper,
            mock(RetryingPersistenceService.class),
            mock(KafkaTemplate.class),
            autoAttachmentService);

    consumer.persist(
        List.of(
            new ConsumerRecord<String, Feature>("opencop.geo.validated", 0, 1L, "first", first),
            new ConsumerRecord<String, Feature>("opencop.geo.validated", 0, 2L, "second", second)));

    verify(autoAttachmentService).attachFromRecord(sourceId, "obj-1");
    verify(autoAttachmentService).attachFromRecord(sourceId, "obj-2");
  }

  private static PostGisFeatureRecord record(UUID sourceId, String objectId) {
    return new PostGisFeatureRecord(
        UUID.randomUUID(),
        "TRACK",
        sourceId,
        null,
        objectId,
        null,
        null,
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        "{\"type\":\"Feature\"}");
  }

  private static String header(ProducerRecord<String, Feature> producerRecord, String headerName) {
    var header = producerRecord.headers().lastHeader(headerName);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
