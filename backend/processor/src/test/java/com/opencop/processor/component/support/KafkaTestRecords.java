// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

public final class KafkaTestRecords {

  private KafkaTestRecords() {}

  public static ProducerRecord<String, byte[]> byteArrayRecord(
      String topic, String key, byte[] payload, Map<String, String> headers) {
    var record = new ProducerRecord<String, byte[]>(topic, key, payload);
    headers.forEach(
        (headerName, headerValue) ->
            record
                .headers()
                .add(new RecordHeader(headerName, headerValue.getBytes(StandardCharsets.UTF_8))));
    return record;
  }

  public static String stringHeader(ConsumerRecord<String, ?> record, String headerName) {
    var header = record.headers().lastHeader(headerName);
    if (header == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
