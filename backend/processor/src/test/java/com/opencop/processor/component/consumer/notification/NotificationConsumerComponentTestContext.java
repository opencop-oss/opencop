// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.notification;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.broker.KafkaListenerFactoryBuilder;
import com.opencop.processor.technical.spring.consumer.NotificationConsumer;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

@TestConfiguration
@Import(NotificationConsumer.class)
public class NotificationConsumerComponentTestContext {

  @Bean
  NotificationHandler<OpencopNotification> notificationHandler() {
    return mock(NotificationHandler.class);
  }

  @Bean(name = "notificationFactory")
  ConcurrentKafkaListenerContainerFactory<String, byte[]> notificationFactory(
      EmbeddedKafkaBroker broker,
      KafkaTemplate<String, byte[]> componentByteArrayDltKafkaTemplate) {
    var listenerProps = new KafkaConsumersProperties.ListenerProps();
    listenerProps.setGroupId("processor-notification-component-it");
    listenerProps.setConcurrency(1);
    listenerProps.setBatch(false);
    listenerProps.setMaxPollRecords(1);
    listenerProps.setMaxRetries(1);
    listenerProps.setMaxWaitMs(500);
    listenerProps.setMinSizeBytes(1);
    listenerProps.setMaxSizeBytes(1_048_576);
    listenerProps.setValueType("notification");

    return new KafkaListenerFactoryBuilder()
        .byteArrayFactory(
            Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
            listenerProps,
            250L,
            componentByteArrayDltKafkaTemplate);
  }
}
