// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.UUID;

/**
 * Whether an integration lets a connection write with an egress mapper. Whether it can write at all
 * is {@link AllowedAccessModesPolicy}'s to decide; one that can permits only the mappers its rule
 * names, or any when the rule is open. Whether the mapper is usable is {@link
 * EgressMapperAvailabilityPolicy}'s to decide.
 */
public final class EgressMapperSelectionPolicy {

  private EgressMapperSelectionPolicy() {}

  /** Rejects an egress mapper the integration's TCP declaration does not let it write with. */
  public static void assertPermits(MapperFramingDeclaration declaration, UUID egressMapperId) {
    // Selecting an egress mapper means writing through it, whatever else the connection does.
    AllowedAccessModesPolicy.assertAllows(TransportProtocol.TCP, declaration, AccessMode.WRITE);
    // A declaration that lets the connection write always declares the rule it writes under.
    var rule = declaration.egress().orElseThrow();
    if (!rule.allows(egressMapperId)) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.MAPPER_NOT_ALLOWED,
          ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    }
  }
}
