// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.UUID;

/** The integration sets up connections the platform cannot do without, so it stays active. */
public final class IntegrationNotDeactivatableException extends RuntimeException {

  public static final String CODE = "INTEGRATION_NOT_DEACTIVATABLE";

  public IntegrationNotDeactivatableException(UUID integrationId) {
    super("Integration " + integrationId + " cannot be deactivated");
  }
}
