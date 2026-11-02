// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.access;

import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class MicrometerAccessModeTransitionRecorder implements AccessModeTransitionRecorder {

  public static final String TRANSITIONS_TOTAL = "opencop_connect_access_mode_transitions_total";
  public static final String INCOMPATIBILITIES_TOTAL =
      "opencop_connect_access_mode_incompatibilities_total";

  private static final String NO_PREVIOUS_MODE = "NONE";

  private final MeterRegistry meterRegistry;

  public MicrometerAccessModeTransitionRecorder(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
  }

  @Override
  public void record(
      TransportKind transport,
      @Nullable AccessMode previousAccess,
      AccessMode newAccess,
      Cause cause) {
    meterRegistry
        .counter(
            TRANSITIONS_TOTAL,
            "transport",
            transport.name(),
            "old_mode",
            previousAccess == null ? NO_PREVIOUS_MODE : previousAccess.getAccess(),
            "new_mode",
            newAccess.getAccess(),
            "cause",
            cause.label())
        .increment();
  }

  @Override
  public void recordIncompatibility(TransportKind transport, AccessMode access) {
    meterRegistry
        .counter(
            INCOMPATIBILITIES_TOTAL,
            "transport",
            transport.name(),
            "mode",
            access.getAccess(),
            "cause",
            "incompatible_transport_access")
        .increment();
  }
}
