// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.job.JobType;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusNotification;
import com.opencop.core.sdk.model.notification.content.JobStatusType;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class UserNotificationHandlerTest {

  private static final String DESTINATION = "/user/queue/notification";

  private final SocketProducer producer = Mockito.mock(SocketProducer.class);
  private final UserNotificationHandler handler = new UserNotificationHandler(producer);
  private final ObjectMapper deserializer = new ObjectMapper();

  @Test
  void serializesNotificationWithInstantAndRoundTripsThroughDeserializingMapper() {
    UUID targetUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    OpencopNotification notification =
        new OpencopNotification(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440004"),
            targetUserId,
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

    handler.notify(targetUserId, notification);

    ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(producer)
        .sendToUser(eq(targetUserId), eq(DESTINATION), payloadCaptor.capture(), anyBoolean());

    OpencopNotification roundTripped =
        deserializer.readValue(payloadCaptor.getValue(), OpencopNotification.class);

    assertEquals(notification, roundTripped);
  }
}
