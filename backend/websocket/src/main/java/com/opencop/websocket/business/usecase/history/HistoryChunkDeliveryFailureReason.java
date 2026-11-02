// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

/**
 * Single source of truth for the stable failure-reason vocabulary surfaced by the websocket-side
 * history chunk delivery pipeline. The {@link #wireValue()} strings are part of the telemetry /
 * dashboard / alert contract — do not rename without coordinating downstream.
 */
public enum HistoryChunkDeliveryFailureReason {
  PAYLOAD_KEY_INVALID("PAYLOAD_KEY_INVALID"),
  JOB_ID_MISSING("JOB_ID_MISSING"),
  PAYLOAD_PART_MISSING("PAYLOAD_PART_MISSING"),
  PAYLOAD_DECODE_FAILED("PAYLOAD_DECODE_FAILED"),
  PAYLOAD_SEND_FAILED("PAYLOAD_SEND_FAILED"),
  PAYLOAD_SEND_TIMEOUT("PAYLOAD_SEND_TIMEOUT"),
  PAYLOAD_SEND_INTERRUPTED("PAYLOAD_SEND_INTERRUPTED"),
  NO_ACTIVE_HISTORY_SUBSCRIPTION("NO_ACTIVE_HISTORY_SUBSCRIPTION");

  private final String wireValue;

  HistoryChunkDeliveryFailureReason(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
