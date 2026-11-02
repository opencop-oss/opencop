// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders.OPENCOP_TYPE;

import com.opencop.core.sdk.ports.gateway.broker.BrokerMessage;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaProducer<T> implements MessageBrokerProducer<T> {

  private final String topic;
  private final KafkaTemplate<String, T> kafkaTemplate;

  public KafkaProducer(KafkaTemplate<String, T> kafkaTemplate, String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Override
  public void send(T event, Map<String, String> headers) {
    kafkaTemplate.send(producerRecord(BrokerMessage.of(event, headers)));
  }

  @Override
  public void sendDlt(T event) {
    kafkaTemplate.send(producerRecord(BrokerMessage.of(event)));
  }

  public void send(T event) {
    this.send(event, Map.of());
  }

  @Override
  public void sendAll(Collection<BrokerMessage<T>> messages) {
    messages.forEach(message -> kafkaTemplate.send(producerRecord(message)));
  }

  private ProducerRecord<String, T> producerRecord(BrokerMessage<T> message) {
    ProducerRecord<String, T> producerRecord =
        new ProducerRecord<>(topic, message.key(), message.event());
    message
        .headers()
        .forEach(
            (key, value) ->
                producerRecord.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
    if (message.event() != null) {
      producerRecord
          .headers()
          .add(
              OPENCOP_TYPE.getName(),
              message.event().getClass().getName().getBytes(StandardCharsets.UTF_8));
    }
    return producerRecord;
  }
}
