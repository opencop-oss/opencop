// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.UUID;

/** Writes runtime observations for socket and BYOD sources. */
public interface SourceRuntimeObservationWriter {

  void writeState(
      UUID sourceId, ConnectorState state, String observedInstanceId, Duration observationTtl);

  void writeRuntime(RuntimeStatus runtime, Duration observationTtl);
}
