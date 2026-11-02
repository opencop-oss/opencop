// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;

/** A raw access value must name an access mode: R, W, or RW. */
public final class AccessModeFormatPolicy {

  private AccessModeFormatPolicy() {}

  public static AccessMode parse(String rawAccess) {
    try {
      return AccessMode.fromString(rawAccess);
    } catch (IllegalArgumentException _) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.INVALID_ACCESS_MODE);
    }
  }
}
