// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.capability;

import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import java.time.Duration;

/**
 * Requests connection restart after receive activity remains idle for a positive threshold.
 *
 * <p>This capability is independent from receive-idle messages and recurring sends. At most one may
 * be published for a connection schema version.
 */
public interface ReceiveIdleReconnectBehavior<S> extends ConnectionBehaviorContribution<S> {

  /** Returns the required positive receive-idle reconnect threshold. */
  Duration reconnectAfterIdle(S settings);
}
