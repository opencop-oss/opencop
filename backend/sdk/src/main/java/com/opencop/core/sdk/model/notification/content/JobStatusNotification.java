// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.job.JobType;
import com.opencop.core.sdk.model.notification.NotificationType;
import jakarta.annotation.Nullable;
import java.util.UUID;

public record JobStatusNotification(
    UUID jobId, JobType jobType, JobStatusType status, @Nullable String detail)
    implements NotificationContent {

  @JsonProperty("type")
  public NotificationType type() {
    return NotificationType.JOB_STATUS;
  }

  @Override
  public String templateKey() {
    return "notification.job.status." + status.name().toLowerCase();
  }
}
