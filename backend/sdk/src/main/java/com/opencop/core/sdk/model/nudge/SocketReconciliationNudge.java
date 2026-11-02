// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.nudge;

public record SocketReconciliationNudge(int version) {
  public static final int CURRENT_VERSION = 1;
  public static final String CHANNEL = "opencop_connect_reconciliation_nudges";

  public static SocketReconciliationNudge current() {
    return new SocketReconciliationNudge(CURRENT_VERSION);
  }
}
