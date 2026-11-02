// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaProducerTest {

  private static final String TOPIC = "mapped-output";

  @Test
  void sendAll_preservesBrokerMessageKeyAndHeaders() {
    KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
    var producer = new KafkaProducer<>(kafkaTemplate, TOPIC);

    producer.sendAll(
        List.of(BrokerMessage.withKey("source-key", "payload", Map.of("trace-id", "abc"))));

    ArgumentCaptor<ProducerRecord<String, String>> records = producerRecordCaptor();
    verify(kafkaTemplate).send(records.capture());
    ProducerRecord<String, String> sentRecord = records.getValue();
    assertThat(sentRecord.topic()).isEqualTo(TOPIC);
    assertThat(sentRecord.key()).isEqualTo("source-key");
    assertThat(sentRecord.value()).isEqualTo("payload");
    assertThat(header(sentRecord, "trace-id")).isEqualTo("abc");
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor() {
    return ArgumentCaptor.forClass(ProducerRecord.class);
  }

  private static String header(ProducerRecord<String, String> record, String headerName) {
    return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
  }
}
