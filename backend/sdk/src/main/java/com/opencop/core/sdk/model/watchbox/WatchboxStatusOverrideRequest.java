// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.List;
import java.util.Set;

public record WatchboxStatusOverrideRequest(
    WatchboxAlertingStatus alertingStatus,
    Set<WatchboxStatusReason> alertingReasons,
    List<WatchboxStatusOverrideSourceStatus> sourceStatuses) {

  public WatchboxStatusOverrideRequest {
    alertingReasons = alertingReasons == null ? null : Set.copyOf(alertingReasons);
    sourceStatuses = sourceStatuses == null ? List.of() : List.copyOf(sourceStatuses);
  }
}
