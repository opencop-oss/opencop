// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.policy.MapperQualifierPolicy;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperAvailabilityPolicy;
import java.util.Optional;

/**
 * Whether a connection may select a mapper to read with: the catalog must hold it as an ingress
 * mapper, and it must not be archived. Unlike an egress mapper it need not come from a plugin,
 * since a user may author one.
 */
public final class IngressMapperSelectionPolicy {

  private IngressMapperSelectionPolicy() {}

  /**
   * The selected ingress mapper, once it is known to be selectable, deciding who answers for one
   * that is not. A requester who chose the mapper freely, under an open declaration, has chosen
   * badly. One the integration imposes, under a closed declaration, is the platform's to keep
   * available, so its absence is a server failure rather than the requester's to fix.
   *
   * @param declaration the integration's mapper rule, which already permits the mapper
   * @param catalogEntry the latest catalog revision under the selected identifier, if any
   */
  public static MapperProfile requireSelectable(
      MapperDeclaration declaration, Optional<MapperProfile> catalogEntry) {
    if (declaration.open()) {
      return requireSelectable(catalogEntry);
    }
    var selectable =
        catalogEntry
            .filter(MapperQualifierPolicy::decodes)
            .filter(IngressMapperAvailabilityPolicy::isAvailable);
    return selectable.orElseThrow(
        () ->
            new ConnectionManagementException(ConnectionExceptionCodes.SYSTEM_MAPPER_UNAVAILABLE));
  }

  /**
   * The selected ingress mapper, once it is known to be selectable.
   *
   * @param catalogEntry the latest catalog revision under the selected identifier, if any
   */
  public static MapperProfile requireSelectable(Optional<MapperProfile> catalogEntry) {
    var mapper =
        catalogEntry
            .filter(MapperQualifierPolicy::decodes)
            .orElseThrow(() -> rejection(ConnectionExceptionCodes.MAPPER_NOT_FOUND));
    if (!IngressMapperAvailabilityPolicy.isAvailable(mapper)) {
      throw rejection(ConnectionExceptionCodes.MAPPER_ARCHIVED);
    }
    return mapper;
  }

  /** A rejection the requester can fix by selecting another ingress mapper. */
  private static ConnectionManagementException rejection(ConnectionExceptionCodes code) {
    return ConnectionManagementException.forField(
        code, ConnectionCreationDetails.INGRESS_MAPPER_FIELD);
  }
}
