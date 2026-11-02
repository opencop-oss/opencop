// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaNaming.DLT_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;

class ByodDeadLetterListenerContainerFactoryTest {

  @Test
  void createsBatchAckDltContainerThatIndexesWithSourceContextBeforeAcknowledging() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var dltTopic = topic + DLT_SUFFIX;
    var indexer = mock(DeadLetterFailureIndexer.class);
    var factory = factory(indexer);

    var dltContainer = factory.create(sourceId, topic);

    try {
      assertEquals(dltTopic, dltContainer.getContainerProperties().getTopics()[0]);
      assertEquals(
          "processor-byod-" + sourceId + "-dlt-indexer",
          dltContainer.getContainerProperties().getGroupId());
      assertEquals(
          ContainerProperties.AckMode.MANUAL, dltContainer.getContainerProperties().getAckMode());
      assertEquals(DefaultErrorHandler.class, dltContainer.getCommonErrorHandler().getClass());

      @SuppressWarnings("unchecked")
      var listener =
          (BatchAcknowledgingMessageListener<String, byte[]>)
              dltContainer.getContainerProperties().getMessageListener();
      var acknowledgment = mock(Acknowledgment.class);
      var dltMessage =
          new ConsumerRecord<>(
              dltTopic, 0, 1L, "key-1", "payload".getBytes(StandardCharsets.UTF_8));
      var batch = java.util.List.of(dltMessage);

      listener.onMessage(batch, acknowledgment);

      assertEquals(
          "{\"sourceId\":\"" + sourceId + "\"}",
          new String(
              dltMessage.headers().lastHeader(DeadLetterFailureIndexer.CONTEXT_HEADER).value(),
              StandardCharsets.UTF_8));
      verify(indexer).indexBatch(batch);
      verify(acknowledgment).acknowledge();
    } finally {
      dltContainer.stop();
    }
  }

  @Test
  void batchListenerDoesNotAcknowledgeWhenIndexingFails() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var dltTopic = topic + DLT_SUFFIX;
    var indexer = mock(DeadLetterFailureIndexer.class);
    var factory = factory(indexer);
    var dltContainer = factory.create(sourceId, topic);

    try {
      @SuppressWarnings("unchecked")
      var listener =
          (BatchAcknowledgingMessageListener<String, byte[]>)
              dltContainer.getContainerProperties().getMessageListener();
      var acknowledgment = mock(Acknowledgment.class);
      var dltMessage =
          new ConsumerRecord<>(
              dltTopic, 0, 1L, "key-1", "payload".getBytes(StandardCharsets.UTF_8));
      var batch = java.util.List.of(dltMessage);
      doThrow(new IllegalStateException("db unavailable")).when(indexer).indexBatch(batch);

      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalStateException.class, () -> listener.onMessage(batch, acknowledgment));

      verify(acknowledgment, never()).acknowledge();
    } finally {
      dltContainer.stop();
    }
  }

  private static ByodDeadLetterListenerContainerFactory factory(DeadLetterFailureIndexer indexer) {
    return new ByodDeadLetterListenerContainerFactory(
        new BaseKafkaConfig(Map.of()),
        indexer,
        new DeadLetterIndexingErrorHandlerFactory(
            new DeadLetterObservability(ProcessorMetrics.noop())),
        new KafkaConsumersProperties());
  }
}
