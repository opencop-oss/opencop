// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.behavior.message;

import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;

/** Common contract for trigger capabilities that produce an outbound connection message. */
public interface MessageProducingBehavior<S> extends ConnectionBehaviorContribution<S> {

  /**
   * Returns the required stateless factory invoked afresh for every execution attempt, bound to the
   * supplied effective settings.
   */
  ConnectionMessageFactory messageFactory(S settings);
}
