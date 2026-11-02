// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.socket;

public enum ConnectorState {
  NEW,
  CONNECTING,
  CONNECTED,
  DISCONNECTED,
  STOPPED,
  INCOMPATIBLE,
  FAILED,
  /** Used when connect service is unreachable and status cannot be determined. */
  UNKNOWN;

  /**
   * Returns true if this state indicates the connection is active or attempting to be active.
   * Active states: NEW, CONNECTING, CONNECTED, DISCONNECTED (will auto-reconnect). Inactive states:
   * STOPPED, INCOMPATIBLE, FAILED, UNKNOWN.
   */
  public boolean isActive() {
    return this == NEW || this == CONNECTING || this == CONNECTED || this == DISCONNECTED;
  }
}
