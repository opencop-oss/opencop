// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.observability.integration;

import com.opencop.core.sdk.model.connection.add.TransportKind;

/** Records when this runtime finds a connection's integration unavailable. */
public interface IntegrationAvailabilityRecorder {

  /**
   * One connection has just become unavailable here, or now selects a different unavailable
   * integration schema version; repeated evaluations of the same selection are not recorded.
   */
  void recordUnavailable(TransportKind transport);
}
