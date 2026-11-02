// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.notification.NotificationType;
import jakarta.annotation.Nullable;
import java.util.UUID;

public record LayerAccessNotification(
    UUID layerId, AccessOperationType operationType, @Nullable String role)
    implements NotificationContent {

  @JsonProperty("type")
  public NotificationType type() {
    return NotificationType.LAYER_ACCESS;
  }

  @Override
  public String templateKey() {
    return "notification.layer.access." + operationType.name().toLowerCase();
  }
}
