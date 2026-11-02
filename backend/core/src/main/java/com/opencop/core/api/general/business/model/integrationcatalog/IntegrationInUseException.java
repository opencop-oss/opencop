// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import java.util.List;

public final class IntegrationInUseException extends RuntimeException {

  public static final String CODE = "INTEGRATION_IN_USE";
  public static final String MESSAGE = "Integration is referenced by non-archived connections";

  private final List<ConnectionMetadata> connections;

  public IntegrationInUseException(List<ConnectionMetadata> connections) {
    super(MESSAGE);
    this.connections = List.copyOf(connections);
  }

  public List<ConnectionMetadata> connections() {
    return connections;
  }
}
