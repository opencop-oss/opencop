// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.notification.NotificationType;
import jakarta.annotation.Nullable;
import java.util.UUID;

public record LayerSourceNotification(
    UUID affectedId, UUID targetId, AccessOperationType operationType, @Nullable String role)
    implements NotificationContent {

  @JsonProperty("type")
  public NotificationType type() {
    return NotificationType.LAYER_SOURCE;
  }

  @Override
  public String templateKey() {
    return "notification.layer.access.source." + operationType.name().toLowerCase();
  }
}
