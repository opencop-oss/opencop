// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.notification;

public enum NotificationType {
  SOURCE_ACCESS("SOURCE_ACCESS"),
  LAYER_ACCESS("LAYER_ACCESS"),
  LAYER_SOURCE("LAYER_SOURCE"),
  JOB_STATUS("JOB_STATUS");

  private final String type;

  NotificationType(String type) {
    this.type = type;
  }

  public String getName() {
    return this.type;
  }
}
