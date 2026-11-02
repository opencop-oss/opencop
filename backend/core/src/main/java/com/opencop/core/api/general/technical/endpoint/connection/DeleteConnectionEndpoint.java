// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection;

import com.opencop.core.api.general.ports.usecase.connection.delete.DeleteConnection;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.NotificationType;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.DataSourceAccessNotification;
import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeleteConnectionEndpoint {

  private final DeleteConnection usecase;
  private final NotificationWorker notificationService;

  DeleteConnectionEndpoint(
      final DeleteConnection usecase, final NotificationWorker notificationService) {
    this.usecase = usecase;
    this.notificationService = notificationService;
  }

  @DeleteMapping("/v1/connections/{sourceId}")
  public ResponseEntity<Void> deleteConnection(
      @AuthenticationPrincipal final OpenCopPrincipal authentication, @PathVariable UUID sourceId) {

    Set<SourceUser> users = usecase.delete(sourceId, authentication);

    NotificationContent content =
        new DataSourceAccessNotification(sourceId, AccessOperationType.DELETED, null);
    List<NotificationCommand> commands = new ArrayList<>();
    UUID correlationId = null;
    if (users.size() > 1) {
      correlationId = UUID.randomUUID();
    }
    for (SourceUser user : users) {
      commands.add(
          new NotificationCommand(
              correlationId,
              authentication.internalUserId(),
              user.getUserId(),
              NotificationType.SOURCE_ACCESS,
              NotificationSeverityType.IMPORTANT,
              content));
    }
    this.notificationService.sendAllAsync(commands);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
