// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.business.model.connection.access.WriteAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.UUID;

/**
 * An access mode is only as good as the mappers behind it: reading requires an ingress mapper,
 * writing an egress mapper, and reading and writing both.
 */
public final class ConnectionAccessPolicy {

  private ConnectionAccessPolicy() {}

  /**
   * The access a creation selects, rejecting a mapper its mode does not use rather than dropping
   * it, since a requester who sent one expected it to matter. Every rejection names the request
   * field of the mapper it concerns, whether that mapper is missing or not accepted.
   */
  public static ConnectionAccess resolveSelection(
      AccessMode mode, UUID ingressMapperId, UUID egressMapperId) {
    if (!mode.canRead() && ingressMapperId != null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.INGRESS_MAPPER_NOT_ACCEPTED,
          ConnectionCreationDetails.INGRESS_MAPPER_FIELD,
          mode);
    }
    if (!mode.canWrite() && egressMapperId != null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.EGRESS_MAPPER_NOT_ACCEPTED,
          ConnectionCreationDetails.EGRESS_MAPPER_FIELD,
          mode);
    }
    return switch (mode) {
      case READ -> new ReadAccess(requireIngress(mode, ingressMapperId));
      case WRITE -> new WriteAccess(requireEgress(mode, egressMapperId));
      case READ_WRITE ->
          new ReadWriteAccess(
              requireIngress(mode, ingressMapperId), requireEgress(mode, egressMapperId));
    };
  }

  private static UUID requireIngress(AccessMode mode, UUID ingressMapperId) {
    if (ingressMapperId == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.INGRESS_MAPPER_REQUIRED,
          ConnectionCreationDetails.INGRESS_MAPPER_FIELD,
          mode);
    }
    return ingressMapperId;
  }

  private static UUID requireEgress(AccessMode mode, UUID egressMapperId) {
    if (egressMapperId == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.EGRESS_MAPPER_REQUIRED,
          ConnectionCreationDetails.EGRESS_MAPPER_FIELD,
          mode);
    }
    return egressMapperId;
  }
}
