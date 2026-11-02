// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;

/**
 * What the fixture extension runs in its script operation, set by the test: several store calls in
 * one invocation, with the test's own committed changes between them.
 */
final class ExtensionScript {

  @FunctionalInterface
  interface Steps {
    void run(ConnectionExtensionContext<NoConnectionSettings> context)
        throws ConnectionExtensionException;
  }

  private static final Steps NOTHING = _ -> {};

  private volatile Steps steps = NOTHING;

  void set(Steps steps) {
    this.steps = steps;
  }

  void run(ConnectionExtensionContext<NoConnectionSettings> context)
      throws ConnectionExtensionException {
    steps.run(context);
  }

  void reset() {
    steps = NOTHING;
  }
}
