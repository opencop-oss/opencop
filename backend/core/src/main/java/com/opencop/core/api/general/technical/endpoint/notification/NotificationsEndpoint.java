// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsEndpoint {

  private final NotificationService service;

  NotificationsEndpoint(final NotificationService service) {
    this.service = service;
  }

  @GetMapping(value = "/v1/notifications")
  public ResponseEntity<PageResponse<OpencopNotification>> getNotifications(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
      @PageableDefault(size = 20) Pageable pageable) {

    PageResponse<OpencopNotification> notifications;
    if (unreadOnly) {
      notifications = service.getUnread(authentication.internalUserId(), pageable);
    } else {
      notifications = service.getAll(authentication.internalUserId(), pageable);
    }
    return ResponseEntity.status(HttpStatus.OK).body(notifications);
  }

  @PostMapping(value = "/v1/notifications/{notificationId}/read")
  public ResponseEntity<Void> markNotificationAsRead(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable("notificationId") UUID notificationId) {

    try {
      this.service.markAsRead(notificationId, authentication.internalUserId());
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
