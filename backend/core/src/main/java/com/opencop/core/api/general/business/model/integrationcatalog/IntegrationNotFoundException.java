// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.UUID;

public final class IntegrationNotFoundException extends RuntimeException {

  public static final String CODE = "INTEGRATION_NOT_FOUND";

  public IntegrationNotFoundException(UUID integrationId) {
    super("Integration " + integrationId + " was not found");
  }
}
