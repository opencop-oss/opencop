// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

public class IntegrationCatalogConflictException extends IllegalStateException {

  public IntegrationCatalogConflictException(String message) {
    super(message);
  }

  public IntegrationCatalogConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
