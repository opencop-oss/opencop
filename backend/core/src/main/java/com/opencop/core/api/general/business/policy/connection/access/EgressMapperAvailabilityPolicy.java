// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import java.util.Optional;

/**
 * Whether a connection can write with an egress mapper: an integration must contribute it, and its
 * catalog entry must be an egress mapper that is not archived.
 */
public final class EgressMapperAvailabilityPolicy {

  private EgressMapperAvailabilityPolicy() {}

  /**
   * The contributed egress mapper, once it is known to be usable.
   *
   * @param contribution the mapper an integration contributes under the identifier, if any
   * @param catalogEntry the latest catalog revision under the identifier, if any
   */
  public static PayloadEgressMapper requireAvailable(
      Optional<PayloadEgressMapper> contribution, Optional<MapperProfile> catalogEntry) {
    return requireAvailable(contribution, catalogEntry, null);
  }

  /**
   * The contributed egress mapper, once it is known to be usable, naming the request field that
   * selected it in any rejection.
   *
   * @param field the request field the egress mapper was selected through, or null for none
   */
  public static PayloadEgressMapper requireAvailable(
      Optional<PayloadEgressMapper> contribution,
      Optional<MapperProfile> catalogEntry,
      String field) {
    if (contribution.isEmpty() || !isEgressMapper(catalogEntry)) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.MAPPER_NOT_FOUND, field);
    }
    if (isArchived(catalogEntry.orElseThrow())) {
      throw ConnectionManagementException.forField(ConnectionExceptionCodes.MAPPER_ARCHIVED, field);
    }
    return contribution.orElseThrow();
  }

  private static boolean isEgressMapper(Optional<MapperProfile> catalogEntry) {
    return catalogEntry.map(MapperProfile::qualifier).orElse(null) == MapperQualifier.EGRESS;
  }

  private static boolean isArchived(MapperProfile catalogEntry) {
    return Boolean.TRUE.equals(catalogEntry.archived());
  }
}
