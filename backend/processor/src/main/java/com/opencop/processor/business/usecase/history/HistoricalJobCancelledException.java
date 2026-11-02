// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import java.util.UUID;

public class HistoricalJobCancelledException extends RuntimeException {

  private final UUID jobId;

  public HistoricalJobCancelledException(UUID jobId, String message) {
    super(message);
    this.jobId = jobId;
  }

  public UUID jobId() {
    return jobId;
  }
}
