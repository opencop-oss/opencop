// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.sdk.model.notification.NotificationType;
import jakarta.annotation.Nullable;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DataSourceAccessNotification.class, name = "SOURCE_ACCESS"),
  @JsonSubTypes.Type(value = LayerAccessNotification.class, name = "LAYER_ACCESS"),
  @JsonSubTypes.Type(value = LayerSourceNotification.class, name = "LAYER_SOURCE"),
  @JsonSubTypes.Type(value = JobStatusNotification.class, name = "JOB_STATUS")
})
public sealed interface NotificationContent
    permits DataSourceAccessNotification,
        LayerAccessNotification,
        LayerSourceNotification,
        JobStatusNotification {

  @JsonProperty("type")
  NotificationType type();

  // For the UI to use the proper notification template
  @JsonProperty("templateKey")
  @Nullable
  default String templateKey() {
    return null;
  }
}
