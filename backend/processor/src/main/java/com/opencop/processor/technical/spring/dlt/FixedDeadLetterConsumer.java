// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class FixedDeadLetterConsumer {

  private final DeadLetterFailureIndexer indexer;

  public FixedDeadLetterConsumer(DeadLetterFailureIndexer indexer) {
    this.indexer = indexer;
  }

  public void index(
      List<ConsumerRecord<String, byte[]>> dltMessages, Acknowledgment acknowledgment) {
    indexer.indexBatch(dltMessages);
    acknowledgment.acknowledge();
  }
}
