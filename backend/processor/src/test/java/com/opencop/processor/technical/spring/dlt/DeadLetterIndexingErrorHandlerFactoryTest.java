// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.MessageListenerContainer;

class DeadLetterIndexingErrorHandlerFactoryTest {

  @Test
  void retryExhaustionRecordsTerminalIndexingFailureAndPropagatesRecoveryFailure() {
    var registry = new SimpleMeterRegistry();
    var factory =
        new DeadLetterIndexingErrorHandlerFactory(
            new DeadLetterObservability(new ProcessorMetrics(registry)));
    var dltMessage =
        new ConsumerRecord<String, byte[]>(
            "opencop.geo.validated.dlt", 2, 91L, "key", "payload".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(
            () ->
                factory.recover(
                    "opencop.geo.validated.dlt",
                    dltMessage,
                    new IllegalStateException("db unavailable")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "DLT indexing retry exhausted; leaving offsets uncommitted for opencop.geo.validated.dlt-2@91")
        .hasCauseInstanceOf(IllegalStateException.class);

    assertThat(
            registry
                .counter(
                    DeadLetterObservability.INDEXING_FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    "opencop.geo.validated.dlt",
                    DeadLetterObservability.TAG_CAUSE,
                    "retry_exhausted")
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .find(DeadLetterObservability.RECORDS_METRIC)
                .tag(DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS, "opencop.geo.validated.dlt")
                .tag(DeadLetterObservability.TAG_OUTCOME, "skipped")
                .counter())
        .isNull();
  }

  @Test
  void retryExhaustionSeeksBatchSoOffsetsRemainUncommitted() {
    var registry = new SimpleMeterRegistry();
    var factory =
        new DeadLetterIndexingErrorHandlerFactory(
            new DeadLetterObservability(new ProcessorMetrics(registry)));
    var properties = new KafkaConsumersProperties.DeadLetterIndexingProps();
    properties.setRetryAttempts(0);
    properties.setRetryInitialIntervalMs(0L);
    properties.setRetryMaxIntervalMs(0L);
    var handler = factory.create("opencop.geo.validated.dlt", properties);
    var topicPartition = new TopicPartition("opencop.geo.validated.dlt", 2);
    var dltMessage =
        new ConsumerRecord<String, byte[]>(
            topicPartition.topic(),
            topicPartition.partition(),
            91L,
            "key",
            "payload".getBytes(StandardCharsets.UTF_8));
    var records = new ConsumerRecords<>(Map.of(topicPartition, List.of(dltMessage)));
    var consumer = mock(Consumer.class);
    var container = mock(MessageListenerContainer.class);
    when(consumer.assignment()).thenReturn(Set.of(topicPartition));
    when(container.isRunning()).thenReturn(true);

    assertThatThrownBy(
            () ->
                handler.handleBatch(
                    new IllegalStateException("db unavailable"),
                    records,
                    consumer,
                    container,
                    () -> {
                      throw new IllegalStateException("db still unavailable");
                    }))
        .isInstanceOf(KafkaException.class)
        .hasMessage("Seek to current after exception");

    verify(consumer).seek(topicPartition, 91L);
  }
}
