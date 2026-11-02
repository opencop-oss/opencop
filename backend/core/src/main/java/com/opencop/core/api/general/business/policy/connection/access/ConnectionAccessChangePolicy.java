// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;

/**
 * Which access changes a stored connection accepts. A patch can select no egress mapper, so it can
 * neither make a connection writable nor change the access of a writable one: leaving RW would
 * leave an egress mapper nothing writes with, and leaving W would need an ingress mapper the
 * connection does not have.
 */
public final class ConnectionAccessChangePolicy {

  private ConnectionAccessChangePolicy() {}

  /**
   * Checks a change of access. The caller only asks when the access actually changes, and checks
   * separately that the connection's transport allows the requested access.
   */
  public static void assertSupported(AccessMode current, AccessMode requested) {
    if (current.canWrite() || requested.canWrite()) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.ACCESS_CHANGE_UNSUPPORTED, current, requested);
    }
  }
}
