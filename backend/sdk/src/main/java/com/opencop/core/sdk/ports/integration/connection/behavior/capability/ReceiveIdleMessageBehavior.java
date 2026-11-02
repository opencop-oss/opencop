// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.capability;

import com.opencop.core.sdk.ports.integration.connection.behavior.message.MessageProducingBehavior;
import java.time.Duration;

/**
 * Produces a message after a connection has received no inbound message for a positive threshold.
 *
 * <p>Receive activity postpones this trigger without affecting recurring behavior. At most one such
 * capability may be published for a connection schema version. Expected message failures remain
 * non-fatal.
 */
public interface ReceiveIdleMessageBehavior<S> extends MessageProducingBehavior<S> {

  /** Returns the required positive receive-idle threshold. */
  Duration idleThreshold(S settings);
}
