// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.socket;

/**
 * Why a connection's runtime state is {@link ConnectorState#FAILED}, as a bounded code safe to show
 * operators. The values are mirrored by the state_reason CHECK constraint on
 * profiles.source_runtime_observation.
 */
public enum ConnectorStateReason {
  /**
   * The Connect instance owning the connection registers no implementation for the integration
   * schema version the connection references, so it is not started rather than run generically.
   */
  INTEGRATION_UNAVAILABLE
}
