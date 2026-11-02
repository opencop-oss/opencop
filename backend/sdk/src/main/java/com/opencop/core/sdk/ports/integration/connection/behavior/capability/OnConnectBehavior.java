// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.capability;

import com.opencop.core.sdk.ports.integration.connection.behavior.message.MessageProducingBehavior;

/**
 * Produces a message after a session reports connected and before its raw read loop continues.
 *
 * <p>Applicable on-connect behaviors execute sequentially in registry order. Failure of one attempt
 * does not suppress later behaviors or undo the connected state. A cohesive implementation may also
 * implement {@link RecurringBehavior} to send the same message immediately and on a cadence.
 */
public interface OnConnectBehavior<S> extends MessageProducingBehavior<S> {}
