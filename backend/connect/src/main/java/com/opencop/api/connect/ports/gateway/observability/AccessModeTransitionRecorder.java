// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.observability;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import org.jspecify.annotations.Nullable;

/** Records bounded operational telemetry when a managed runtime changes access mode. */
public interface AccessModeTransitionRecorder {

  void record(
      TransportKind transport,
      @Nullable AccessMode previousAccess,
      AccessMode newAccess,
      Cause cause);

  void recordIncompatibility(TransportKind transport, AccessMode access);

  enum Cause {
    RUNTIME_START("runtime_start"),
    DESIRED_STATE_RECONCILIATION("desired_state_reconciliation");

    private final String label;

    Cause(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }
}
