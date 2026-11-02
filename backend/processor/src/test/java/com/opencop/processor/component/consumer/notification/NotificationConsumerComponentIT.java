// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.LayerSourceNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.processor.component.support.AbstractProcessorKafkaComponentIT;
import com.opencop.processor.component.support.KafkaTestRecords;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestApplication;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import com.opencop.processor.technical.spring.consumer.NotificationConsumer;
import com.opencop.processor.technical.spring.dlt.DeadLetterHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = {
      ProcessorKafkaComponentTestApplication.class,
      NotificationConsumerComponentTestContext.class
    })
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "${opencop.messaging-broker.listeners.notification.topic}",
      "${opencop.messaging-broker.listeners.notification.topic}.dlt"
    })
class NotificationConsumerComponentIT extends AbstractProcessorKafkaComponentIT {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired private NotificationHandler<OpencopNotification> notificationHandler;
  @Autowired private NotificationConsumer notificationConsumer;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Value("${opencop.messaging-broker.listeners.notification.topic}")
  private String topic;

  @BeforeEach
  void beforeEach() {
    MessageListenerContainer listenerContainer =
        kafkaListenerEndpointRegistry.getListenerContainer("notificationConsumer");
    assertNotNull(listenerContainer);
    ContainerTestUtils.waitForAssignment(
        listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());
  }

  @AfterEach
  void afterEach() {
    reset(notificationHandler);
  }

  @Nested
  class StartupAndWiring {

    @Test
    void loadsNotificationConsumerWithKafkaListenerWiring() {
      assertNotNull(notificationConsumer);
      assertNotNull(notificationHandler);
    }
  }

  @Nested
  class HappyPath {

    @Test
    void deserializesNotificationPayloadAndDelegatesToUseCase() throws Exception {
      var notification = notification();

      kafkaTemplate.send(topic, "key-1", OBJECT_MAPPER.writeValueAsBytes(notification));
      kafkaTemplate.flush();

      var notificationCaptor = ArgumentCaptor.forClass(OpencopNotification.class);
      verify(notificationHandler, timeout(5000)).notify(notificationCaptor.capture());

      var consumedNotification = notificationCaptor.getValue();
      assertEquals(notification.notificationId(), consumedNotification.notificationId());
      assertEquals(notification.correlationId(), consumedNotification.correlationId());
      assertEquals(notification.issuerUserId(), consumedNotification.issuerUserId());
      assertEquals(notification.targetUserId(), consumedNotification.targetUserId());
      assertEquals(notification.severity(), consumedNotification.severity());
      assertEquals(notification.status(), consumedNotification.status());
      assertEquals(notification.createdAt(), consumedNotification.createdAt());
      assertEquals(notification.read(), consumedNotification.read());
      assertEquals(notification.readAt(), consumedNotification.readAt());
      assertEquals(notification.content(), consumedNotification.content());
    }
  }

  @Nested
  class HeadersAndPayloadFailures {

    @Test
    void doesNotDelegateWhenPayloadIsMalformedJson() throws Exception {
      var key = "key-bad-" + UUID.randomUUID();

      kafkaTemplate.send(topic, key, "{not-json".getBytes());
      kafkaTemplate.flush();

      try (var consumer = dltConsumer("processor-notification-dlt")) {
        var dltRecord =
            KafkaTestUtils.getSingleRecord(consumer, topic + ".dlt", Duration.ofSeconds(10));

        verifyNoInteractions(notificationHandler);
        assertEquals(key, dltRecord.key());
        assertEquals(topic, KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-topic"));
        assertEquals("0", KafkaTestRecords.stringHeader(dltRecord, "kafka_dlt-original-partition"));
        assertEquals(
            "NOTIFICATION", KafkaTestRecords.stringHeader(dltRecord, DeadLetterHeaders.STAGE));
        assertEquals(
            "MALFORMED", KafkaTestRecords.stringHeader(dltRecord, DeadLetterHeaders.FAILURE_KIND));
      }
    }
  }

  private Consumer<String, byte[]> dltConsumer(String groupId) {
    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    var consumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer();
    embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic + ".dlt");
    return consumer;
  }

  private static OpencopNotification notification() {
    return new OpencopNotification(
        UUID.fromString("53ae55a3-56f1-4907-92e0-f8bd6ff51967"),
        UUID.fromString("4f4d3c4c-7196-4c9e-bf3d-9df33d12057e"),
        UUID.fromString("f73ca623-c145-4382-9fb2-ab3f37e56406"),
        UUID.fromString("7c90dab5-fb8e-4ac6-9dad-8eeb9a40fef8"),
        NotificationSeverityType.WARNING,
        new LayerSourceNotification(
            UUID.fromString("a5ffebd8-6350-4278-b09f-a0e740ab2ae4"),
            UUID.fromString("19f10299-85de-4046-becc-ce0bce9ced94"),
            AccessOperationType.ADDED,
            "viewer"),
        NotificationStatus.CREATED,
        Instant.parse("2026-03-18T15:00:00Z"),
        false,
        null);
  }
}
