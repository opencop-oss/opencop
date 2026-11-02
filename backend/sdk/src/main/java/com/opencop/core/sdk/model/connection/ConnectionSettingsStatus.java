// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection;

/** Whether Core resolves a connection's stored integration settings overrides. */
public enum ConnectionSettingsStatus {
  /** The connection has no integration settings to resolve. */
  NONE,
  /** The stored overrides, on top of Core's deployment defaults, satisfy every rule. */
  RESOLVED,
  /**
   * The stored overrides no longer satisfy the rules on top of Core's deployment defaults, after a
   * default changed for instance. Connect then keeps a running connection's current plan and starts
   * a stopped one on the defaults.
   */
  REJECTED
}
