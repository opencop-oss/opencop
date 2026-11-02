// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import java.util.Optional;

public record HistoryChunkDeliveryPreconditionResult(
    boolean valid, Optional<HistoryChunkDeliveryFailureReason> failureReason) {

  static HistoryChunkDeliveryPreconditionResult validResult() {
    return new HistoryChunkDeliveryPreconditionResult(true, Optional.empty());
  }

  static HistoryChunkDeliveryPreconditionResult failedResult(
      HistoryChunkDeliveryFailureReason failureReason) {
    return new HistoryChunkDeliveryPreconditionResult(false, Optional.of(failureReason));
  }
}
