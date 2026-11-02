// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

/**
 * Thrown by the websocket-side delivery pipeline when a chunk send fails for one of the well-known
 * reasons in {@link HistoryChunkDeliveryFailureReason}. Carrying the reason as a typed enum avoids
 * the brittle string-message routing that the sender used to do on caught {@code
 * IllegalStateException}s.
 */
public class HistoryChunkDeliveryException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HistoryChunkDeliveryFailureReason reason;

  public HistoryChunkDeliveryException(HistoryChunkDeliveryFailureReason reason) {
    super(reason.wireValue());
    this.reason = reason;
  }

  public HistoryChunkDeliveryException(HistoryChunkDeliveryFailureReason reason, Throwable cause) {
    super(reason.wireValue(), cause);
    this.reason = reason;
  }

  public HistoryChunkDeliveryFailureReason reason() {
    return reason;
  }
}
