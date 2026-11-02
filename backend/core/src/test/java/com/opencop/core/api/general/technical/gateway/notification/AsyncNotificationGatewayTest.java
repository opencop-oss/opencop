// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.NotificationType;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.DataSourceAccessNotification;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncNotificationGatewayTest {

  private static final UUID SOURCE_ID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a71-b2c3d4e5f607");
  private static final UUID OWNER_ID = UUID.fromString("7a8b9c0d-1e2f-4a3b-9c4d-5e6f7a8b9c0d");

  private final NotificationWorker worker = mock(NotificationWorker.class);
  private final AsyncNotificationGateway gateway = new AsyncNotificationGateway(worker);

  /** The owner is both told and named as the actor; the wire role is the owner's. */
  @Test
  void sendsTheAddedOwnerNotificationToTheOwner() {
    gateway.notifyAddedOwner(SOURCE_ID, OWNER_ID);

    verify(worker)
        .sendAsync(
            new NotificationCommand(
                null,
                OWNER_ID,
                OWNER_ID,
                NotificationType.SOURCE_ACCESS,
                NotificationSeverityType.NONE,
                new DataSourceAccessNotification(SOURCE_ID, AccessOperationType.ADDED, "OWNER")));
  }
}
