// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaListenerFactoryBuilderTest {

  @SuppressWarnings("unchecked")
  @Test
  void listenerAutoOffsetResetWinsOverBasePropsWhenPresentAndAbsentKeepsBaseProps() {
    var template = mock(KafkaTemplate.class);
    var baseProps = Map.<String, Object>of(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    var configured = listenerProps();
    configured.setAutoOffsetReset("latest");

    var configuredFactory =
        new KafkaListenerFactoryBuilder().featureFactory(baseProps, configured, 1_000, template);
    var defaultFactory =
        new KafkaListenerFactoryBuilder()
            .featureFactory(baseProps, listenerProps(), 1_000, template);

    assertEquals(
        "latest",
        configuredFactory
            .getConsumerFactory()
            .getConfigurationProperties()
            .get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
    assertEquals(
        "earliest",
        defaultFactory
            .getConsumerFactory()
            .getConfigurationProperties()
            .get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
  }

  private static KafkaConsumersProperties.ListenerProps listenerProps() {
    var props = new KafkaConsumersProperties.ListenerProps();
    props.setGroupId("test-group");
    return props;
  }

  @SuppressWarnings("unchecked")
  @Test
  void recovererCountsPublishedRecordsByStageAndOnlyAfterASuccessfulPublish() {
    // The DLT-published counter is the only always-on signal attributing permanent loss to a
    // stage, so both halves are pinned: the tag carries the listener's stage, and a failed
    // publish must not be counted as loss (the recoverer throws before the increment).
    var kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    var registry = new SimpleMeterRegistry();
    var recoverer =
        new KafkaListenerFactoryBuilder(new ProcessorMetrics(registry))
            .recovererFor(kafkaTemplate, DeadLetterHeaders.Stage.WATCHBOX);

    recoverer.accept(
        new ConsumerRecord<>("opencop.geo.validated", 0, 1L, "key-1", "payload"),
        new IllegalStateException("boom"));

    assertEquals(
        1.0,
        registry
            .counter(
                KafkaListenerFactoryBuilder.DLT_PUBLISHED_METRIC,
                Tags.of("stage", DeadLetterHeaders.Stage.WATCHBOX.name()))
            .count());

    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dlt down")));
    assertThrows(
        Exception.class,
        () ->
            recoverer.accept(
                new ConsumerRecord<>("opencop.geo.validated", 0, 2L, "key-2", "payload"),
                new IllegalStateException("boom")));
    assertEquals(
        1.0,
        registry
            .counter(
                KafkaListenerFactoryBuilder.DLT_PUBLISHED_METRIC,
                Tags.of("stage", DeadLetterHeaders.Stage.WATCHBOX.name()))
            .count());
  }

  @SuppressWarnings("unchecked")
  @Test
  void recovererPublishesWithoutDestinationPartitionAndPreservesOriginalPartitionHeader() {
    var kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    var recoverer = new KafkaListenerFactoryBuilder().recovererFor(kafkaTemplate);
    var consumerRecord = new ConsumerRecord<>("opencop.geo.validated", 3, 101L, "key-1", "payload");
    var sourceId = UUID.randomUUID();
    var mapperId = UUID.randomUUID();
    var importId = UUID.randomUUID();
    consumerRecord
        .headers()
        .add(
            OpencopKafkaHeaders.SOURCE_ID.getName(),
            sourceId.toString().getBytes(StandardCharsets.UTF_8))
        .add(
            OpencopKafkaHeaders.MAPPER_ID.getName(),
            mapperId.toString().getBytes(StandardCharsets.UTF_8))
        .add(
            OpencopKafkaHeaders.IMPORT_ID.getName(),
            importId.toString().getBytes(StandardCharsets.UTF_8));

    recoverer.accept(consumerRecord, new IllegalStateException("boom"));

    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());

    @SuppressWarnings("unchecked")
    ProducerRecord<String, Object> dltRecord = recordCaptor.getValue();
    assertEquals("opencop.geo.validated.dlt", dltRecord.topic());
    assertNull(dltRecord.partition());
    assertEquals(1, headerCount(dltRecord, "kafka_dlt-original-partition"));
    assertEquals(1, headerCount(dltRecord, "kafka_dlt-exception-message"));
    assertEquals(1, headerCount(dltRecord, DeadLetterHeaders.STAGE));
    assertEquals(1, headerCount(dltRecord, DeadLetterHeaders.FAILURE_KIND));
    assertEquals("3", header(dltRecord, "kafka_dlt-original-partition"));
    assertEquals("opencop.geo.validated", header(dltRecord, "kafka_dlt-original-topic"));
    assertEquals("101", header(dltRecord, "kafka_dlt-original-offset"));
    assertEquals(
        IllegalStateException.class.getName(), header(dltRecord, "kafka_dlt-exception-fqcn"));
    assertEquals("boom", header(dltRecord, "kafka_dlt-exception-message"));
    assertEquals("REAL_TIME", header(dltRecord, DeadLetterHeaders.STAGE));
    assertEquals("PROCESSING", header(dltRecord, DeadLetterHeaders.FAILURE_KIND));
    assertEquals(
        """
        {"sourceId":"%s","mapperId":"%s","importId":"%s"}
        """
            .formatted(sourceId, mapperId, importId)
            .trim(),
        header(dltRecord, DeadLetterFailureIndexer.CONTEXT_HEADER));
    assertNull(dltRecord.headers().lastHeader("kafka_dlt-exception-stacktrace"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void recovererUsesConfiguredStageForSharedTopicsAndRootCauseExceptionMetadata() {
    var kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    var recoverer =
        new KafkaListenerFactoryBuilder()
            .recovererFor(kafkaTemplate, DeadLetterHeaders.Stage.PERSIST);
    var consumerRecord = new ConsumerRecord<>("opencop.geo.validated", 3, 101L, "key-1", "payload");
    var root = new IllegalArgumentException("root failure");

    recoverer.accept(consumerRecord, new IllegalStateException("outer failure", root));

    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());

    @SuppressWarnings("unchecked")
    ProducerRecord<String, Object> dltRecord = recordCaptor.getValue();
    assertEquals("PERSIST", header(dltRecord, DeadLetterHeaders.STAGE));
    assertEquals("MALFORMED", header(dltRecord, DeadLetterHeaders.FAILURE_KIND));
    assertEquals(
        IllegalArgumentException.class.getName(), header(dltRecord, "kafka_dlt-exception-fqcn"));
    assertEquals("root failure", header(dltRecord, "kafka_dlt-exception-message"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void recovererClassifiesMapperEngineFailuresAsMappingErrors() {
    var kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    var recoverer = new KafkaListenerFactoryBuilder().recovererFor(kafkaTemplate);
    var consumerRecord =
        new ConsumerRecord<>("opencop.event.external.raw", 0, 9L, "key-1", "payload");

    recoverer.accept(
        consumerRecord,
        new IllegalStateException("outer", new MapperEngineException("MAPPING", "bad rule", null)));

    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());

    @SuppressWarnings("unchecked")
    ProducerRecord<String, Object> dltRecord = recordCaptor.getValue();
    assertEquals("MAPPING_ERROR", header(dltRecord, DeadLetterHeaders.FAILURE_KIND));
  }

  private static String header(ProducerRecord<String, Object> consumerRecord, String headerName) {
    var header = consumerRecord.headers().lastHeader(headerName);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  private static int headerCount(ProducerRecord<String, Object> consumerRecord, String headerName) {
    int count = 0;
    for (var header : consumerRecord.headers().headers(headerName)) {
      count++;
    }
    return count;
  }
}
