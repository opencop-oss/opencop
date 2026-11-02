// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.cancellation;

import com.opencop.core.sdk.model.history.HistoricalJobCancellationReasons;
import java.util.UUID;

final class HistoricalJobCancellationReasonPolicy {

  private static final String DEFAULT_CANCEL_REASON = "Historical job cancelled";
  private static final String SAME_JOB_REDELIVERY_CANCEL_REASON =
      "Historical job cancelled before processor start";

  private HistoricalJobCancellationReasonPolicy() {}

  static String requestedCancellationReason(String requestedReason) {
    if (requestedReason != null && !requestedReason.isBlank()) {
      return requestedReason;
    }
    return DEFAULT_CANCEL_REASON;
  }

  static String requestedCancellationReason(String requestedReason, UUID replacementRequestId) {
    if (requestedReason != null && !requestedReason.isBlank()) {
      return requestedReason;
    }
    if (replacementRequestId != null) {
      return HistoricalJobCancellationReasons.supersededByRequest(replacementRequestId);
    }
    return SAME_JOB_REDELIVERY_CANCEL_REASON;
  }
}
