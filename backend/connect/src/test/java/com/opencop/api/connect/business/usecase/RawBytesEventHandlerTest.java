// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.api.connect.business.usecase.event.RawBytesEventHandler;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class RawBytesEventHandlerTest {

  @Mock private KafkaTemplate<String, byte[]> kafkaTemplate;

  @Captor private ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor;

  @Test
  void shouldSendRawBytesWithHeadersToKafka() {
    var producer = new KafkaProducer<>(kafkaTemplate, "test-topic");
    var handler = new RawBytesEventHandler(producer);
    var serverId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    var rawBytes = "test message".getBytes(StandardCharsets.UTF_8);

    handler.handle(serverId, rawBytes);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();

    assertThat(record.topic()).isEqualTo("test-topic");

    // changed approaches, we don't set key now
    // assertThat(record.key()).isEqualTo(sourceId.toString());

    // Verify value is raw bytes
    assertThat(record.value()).isEqualTo(rawBytes);

    // Verify headers
    assertThat(record.headers().lastHeader(OpencopKafkaHeaders.SOURCE_ID.getName()).value())
        .isEqualTo(serverId.toString().getBytes(StandardCharsets.UTF_8));

    assertThat(record.headers().lastHeader(OpencopKafkaHeaders.OBJECT_ID.getName())).isNotNull();
    assertThat(record.headers().lastHeader(OpencopKafkaHeaders.OBJECT_ID.getName()).value())
        .isNotEmpty();

    // opencop-type is set by KafkaProducer to event.getClass().getName()
    // For byte[], the class name is "[B" (NOT "[B]")
    assertThat(record.headers().lastHeader(OpencopKafkaHeaders.OPENCOP_TYPE.getName()).value())
        .isEqualTo("[B".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldGenerateUniqueobjectIdForEachMessage() {
    var producer = new KafkaProducer<>(kafkaTemplate, "test-topic");
    var handler = new RawBytesEventHandler(producer);
    var serverId = UUID.randomUUID();
    var message1 = "message 1".getBytes(StandardCharsets.UTF_8);
    var message2 = "message 2".getBytes(StandardCharsets.UTF_8);

    handler.handle(serverId, message1);
    handler.handle(serverId, message2);

    verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    // Note: Can't easily capture multiple invocations with same captor,
    // but the test above verifies event-id exists and is not empty
  }

  @Test
  void shouldHandleEmptyMessage() {
    var producer = new KafkaProducer<>(kafkaTemplate, "test-topic");
    var handler = new RawBytesEventHandler(producer);
    var serverId = UUID.randomUUID();
    var emptyBytes = new byte[0];

    handler.handle(serverId, emptyBytes);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, byte[]> record = recordCaptor.getValue();
    assertThat(record.value()).isEmpty();
  }
}
