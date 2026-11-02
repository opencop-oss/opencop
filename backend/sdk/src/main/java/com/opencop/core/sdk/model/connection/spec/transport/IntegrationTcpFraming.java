// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import java.util.Objects;
import java.util.UUID;

/** References a framing definition an integration declares in code. */
public record IntegrationTcpFraming(UUID framingId) implements TcpFraming {
  static final String KIND = "integration";

  public IntegrationTcpFraming {
    Objects.requireNonNull(framingId, "framingId");
  }
}
