// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

public final class HistoricalJobQueueRole {

  public static final String DISPATCH_READY = "DISPATCH_READY";
  public static final String ACTIVE_REQUEST_BACKLOG = "ACTIVE_REQUEST_BACKLOG";
  public static final String QUEUED_REPLACEMENT = "QUEUED_REPLACEMENT";

  private HistoricalJobQueueRole() {}

  public static void validate(String role) {
    if (DISPATCH_READY.equals(role)
        || ACTIVE_REQUEST_BACKLOG.equals(role)
        || QUEUED_REPLACEMENT.equals(role)) {
      return;
    }
    throw new IllegalArgumentException("Unsupported queue role: " + role);
  }

  static String normalizeForStatus(HistoricalJobStatus status, String queueRole) {
    return status == HistoricalJobStatus.QUEUED ? DISPATCH_READY : queueRole;
  }
}
