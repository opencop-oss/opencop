// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.capability;

import com.opencop.core.sdk.ports.integration.connection.behavior.message.MessageProducingBehavior;
import java.time.Duration;

/**
 * Produces a message repeatedly for a live connection using a fixed delay.
 *
 * <p>The first attempt waits one full positive delay. Every later attempt is independently rearmed
 * one full delay after the preceding attempt completes, including after an expected failure.
 * Multiple recurring behaviors are allowed and run independently in deterministic registry order.
 */
public interface RecurringBehavior<S> extends MessageProducingBehavior<S> {

  /** Returns the required positive delay. */
  Duration delay(S settings);
}
