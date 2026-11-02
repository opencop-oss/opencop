// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class FixedDeadLetterConsumerTest {

  @Test
  void indexesBatchBeforeAcknowledgingOffsets() {
    var indexer = mock(DeadLetterFailureIndexer.class);
    var consumer = new FixedDeadLetterConsumer(indexer);
    var acknowledgment = mock(Acknowledgment.class);
    var batch = List.of(dltMessage(1L), dltMessage(2L));

    consumer.index(batch, acknowledgment);

    verify(indexer).indexBatch(batch);
    verify(acknowledgment).acknowledge();
  }

  @Test
  void doesNotAcknowledgeOffsetsWhenBatchIndexingFails() {
    var indexer = mock(DeadLetterFailureIndexer.class);
    var consumer = new FixedDeadLetterConsumer(indexer);
    var acknowledgment = mock(Acknowledgment.class);
    var batch = List.of(dltMessage(1L), dltMessage(2L));
    doThrow(new IllegalStateException("db unavailable")).when(indexer).indexBatch(batch);

    Assertions.assertThrows(
        IllegalStateException.class, () -> consumer.index(batch, acknowledgment));

    verify(acknowledgment, never()).acknowledge();
  }

  private static ConsumerRecord<String, byte[]> dltMessage(long offset) {
    return new ConsumerRecord<>(
        "opencop.geo.validated.dlt",
        0,
        offset,
        "key-" + offset,
        "payload".getBytes(StandardCharsets.UTF_8));
  }
}
