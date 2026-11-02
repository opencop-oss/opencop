// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawIngestConsumer {

  private static final Logger log = LoggerFactory.getLogger(RawIngestConsumer.class);

  private final RawEventHandler<byte[]> eventIngest;

  RawIngestConsumer(final RawEventHandler<byte[]> eventIngest) {
    this.eventIngest = eventIngest;
  }

  @KafkaListener(
      id = "rawEventConsumer",
      topics = "${opencop.messaging-broker.listeners.raw.topic}",
      containerFactory = "rawFactory")
  public void rawEvent(List<ConsumerRecord<String, byte[]>> records) {
    long startedAtNanos = System.nanoTime();
    int payloadBytes = ConsumerLogSupport.totalPayloadBytes(records);
    log.debug("Consuming raw ingest batch: records={}, bytes={}", records.size(), payloadBytes);
    if (log.isDebugEnabled() && !records.isEmpty()) {
      var first = records.getFirst();
      var last = records.getLast();
      log.debug(
          "Raw ingest batch metadata: firstTopicPartition={}#{}, firstOffset={}, lastOffset={}, firstKey={}, lastKey={}",
          first.topic(),
          first.partition(),
          first.offset(),
          last.offset(),
          first.key(),
          last.key());
    }

    try {
      eventIngest.handle(records);
      log.info(
          "Raw ingest batch processed successfully: records={}, bytes={}, durationMs={}",
          records.size(),
          payloadBytes,
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos));
    } catch (Exception e) {
      log.error(
          "Raw ingest batch failed: records={}, bytes={}, durationMs={}",
          records.size(),
          payloadBytes,
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          e);
      throw e;
    }
  }
}
