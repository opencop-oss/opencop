// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification;

import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

public record OpencopNotification(
    UUID notificationId,
    @Nullable UUID correlationId,
    UUID issuerUserId,
    UUID targetUserId,
    NotificationSeverityType severity,
    NotificationContent content,
    NotificationStatus status,
    Instant createdAt,
    boolean read,
    @Nullable Instant readAt) {}
