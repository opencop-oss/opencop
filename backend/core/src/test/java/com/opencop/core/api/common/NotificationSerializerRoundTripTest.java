// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.job.JobType;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusType;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards notification serializer wire compatibility: the {@code notificationKafkaTemplate} producer
 * uses {@link JacksonJsonSerializer} (Jackson 3), and the notification consumer deserializes the
 * raw JSON body with a plain {@link ObjectMapper}. The serialized {@link OpencopNotification} shape
 * must survive that round-trip.
 */
class NotificationSerializerRoundTripTest {

  private static final String TOPIC = "notifications";
  private static final String TYPE_ID_HEADER = "__TypeId__";

  @Test
  @SuppressWarnings("unchecked")
  void notificationKafkaTemplatePreservesLegacyTimestampsAndTypeHeader() {
    OpencopNotification notification =
        new OpencopNotification(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440004"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            NotificationSeverityType.INFO,
            new JobStatusNotification(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440005"),
                JobType.LAYER_COPY,
                JobStatusType.SUCCEEDED,
                "done"),
            NotificationStatus.SENT,
            Instant.parse("2026-03-12T10:15:30Z"),
            false,
            Instant.parse("2026-03-12T11:20:00Z"));

    var template =
        new MessageBrokerConfig(new BaseKafkaConfig(Map.of())).notificationKafkaTemplate();
    var producerFactory =
        (DefaultKafkaProducerFactory<String, OpencopNotification>) template.getProducerFactory();
    var serializerSupplier = producerFactory.getValueSerializerSupplier();
    assertNotNull(serializerSupplier);

    var headers = new RecordHeaders();
    byte[] payload;
    try (var serializer = serializerSupplier.get()) {
      assertNotNull(serializer);
      payload = serializer.serialize(TOPIC, headers, notification);
    }

    var mapper = new ObjectMapper();
    var json = mapper.readTree(payload);
    assertTrue(json.path("createdAt").isNumber());
    assertTrue(json.path("readAt").isNumber());
    var payloadJson = new String(payload, StandardCharsets.UTF_8);
    assertTrue(payloadJson.contains("\"createdAt\":1773310530.000000000"));
    assertTrue(payloadJson.contains("\"readAt\":1773314400.000000000"));

    var typeHeader = headers.lastHeader(TYPE_ID_HEADER);
    assertNotNull(typeHeader);
    assertEquals(
        OpencopNotification.class.getName(),
        new String(typeHeader.value(), StandardCharsets.UTF_8));

    OpencopNotification roundTripped = mapper.readValue(payload, OpencopNotification.class);
    assertEquals(notification, roundTripped);
  }
}
