// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Objects;

/** Signals that connection setup could not be described, carrying the reason a client is told. */
public final class IntegrationConnectionSetupUnavailableException extends RuntimeException {

  private final IntegrationConnectionSetupRejection reason;

  public IntegrationConnectionSetupUnavailableException(
      IntegrationConnectionSetupRejection reason) {
    super(Objects.requireNonNull(reason, "reason").message());
    this.reason = reason;
  }

  public IntegrationConnectionSetupRejection reason() {
    return reason;
  }
}
