// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import java.util.UUID;

/**
 * Decides whether a selected mapper is one the connection's integration permits, for a transport
 * whose integration declares mappers alone.
 */
public final class MapperDeclarationPolicy {

  private MapperDeclarationPolicy() {}

  public static void assertAllows(MapperDeclaration declaration, UUID mapperId) {
    if (!declaration.allows(mapperId)) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.MAPPER_NOT_ALLOWED,
          ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
    }
  }
}
