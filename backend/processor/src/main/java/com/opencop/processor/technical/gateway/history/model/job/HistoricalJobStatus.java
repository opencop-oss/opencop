// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import java.util.Set;

public enum HistoricalJobStatus {
  RECEIVED,
  QUEUED,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED;

  public static final Set<HistoricalJobStatus> TERMINAL = Set.of(COMPLETED, FAILED, CANCELLED);

  public boolean isTerminal() {
    return TERMINAL.contains(this);
  }
}
