// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.spring.consumer.ByodMessageHandler;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class ByodListenerContainerFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  void recovererPublishesWithoutDestinationPartitionAndPreservesOriginalPartitionHeader() {
    var properties =
        new ByodConsumerProperties(
            100, 2, 1_000, "earliest", 1, 45_000, 3_000, 300_000, 1, 52_428_800);
    var handler = mock(ByodMessageHandler.class);
    var dltTemplate = mock(KafkaTemplate.class);
    when(dltTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    var factory =
        new ByodListenerContainerFactory(
            new BaseKafkaConfig(Map.of()), properties, handler, dltTemplate);
    var recoverer = factory.recoverer();
    var consumerRecord =
        new ConsumerRecord<>(
            "opencop.byod.partition-proof.2db183bf-1a15-4569-b471-47ed8ad2a1fe",
            7,
            11L,
            "key-1",
            "payload".getBytes(StandardCharsets.UTF_8));

    recoverer.accept(consumerRecord, new IllegalStateException("boom"));

    var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(dltTemplate).send(recordCaptor.capture());

    @SuppressWarnings("unchecked")
    ProducerRecord<String, byte[]> dltRecord = recordCaptor.getValue();
    assertEquals(consumerRecord.topic() + ".dlt", dltRecord.topic());
    assertNull(dltRecord.partition());
    assertEquals("7", stringHeader(dltRecord, "kafka_dlt-original-partition"));
    assertEquals(consumerRecord.topic(), stringHeader(dltRecord, "kafka_dlt-original-topic"));
    assertEquals("11", stringHeader(dltRecord, "kafka_dlt-original-offset"));
    assertEquals(
        IllegalStateException.class.getName(), stringHeader(dltRecord, "kafka_dlt-exception-fqcn"));
    assertEquals("boom", stringHeader(dltRecord, "kafka_dlt-exception-message"));
    assertNull(stringHeader(dltRecord, "kafka_dlt-exception-stacktrace"));
    assertEquals("BYOD", stringHeader(dltRecord, DeadLetterHeaders.STAGE));
    assertEquals("PROCESSING", stringHeader(dltRecord, DeadLetterHeaders.FAILURE_KIND));
  }

  private static String stringHeader(
      ProducerRecord<String, byte[]> consumerRecord, String headerName) {
    var header = consumerRecord.headers().lastHeader(headerName);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
