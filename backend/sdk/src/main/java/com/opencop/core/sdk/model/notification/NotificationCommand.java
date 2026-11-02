// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification;

import com.opencop.core.sdk.model.notification.content.NotificationContent;
import java.util.UUID;

public record NotificationCommand(
    UUID correlationId,
    UUID issuerUserId,
    UUID recipientUserId,
    NotificationType type,
    NotificationSeverityType severity,
    NotificationContent content) {}
