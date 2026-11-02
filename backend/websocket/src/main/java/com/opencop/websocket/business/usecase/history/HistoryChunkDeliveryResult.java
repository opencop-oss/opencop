// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

public record HistoryChunkDeliveryResult(boolean success, String failureReason) {

  public static HistoryChunkDeliveryResult deliveredResult() {
    return new HistoryChunkDeliveryResult(true, null);
  }

  /** The user has no active subscribed `/user/queue/history` session to receive the chunk. */
  public static HistoryChunkDeliveryResult noActiveHistorySessionResult() {
    return failedResult(HistoryChunkDeliveryFailureReason.NO_ACTIVE_HISTORY_SUBSCRIPTION);
  }

  public static HistoryChunkDeliveryResult failedResult(HistoryChunkDeliveryFailureReason reason) {
    return new HistoryChunkDeliveryResult(false, reason.wireValue());
  }

  /**
   * Legacy naming retained for behavioral checks; now maps directly to {@link #failureReason()}.
   */
  public boolean noSessionAvailable() {
    return HistoryChunkDeliveryFailureReason.NO_ACTIVE_HISTORY_SUBSCRIPTION
        .wireValue()
        .equals(failureReason);
  }
}
