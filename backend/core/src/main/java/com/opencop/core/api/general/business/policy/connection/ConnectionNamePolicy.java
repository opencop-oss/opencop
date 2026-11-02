// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;

/**
 * A connection's name identifies it to people, so it must say something, and it must fit wherever
 * people read it.
 *
 * <p>The bound is the one Watchbox names already use, counted in Unicode code points so a name in
 * any script gets the same room: 255, the common display-name bound.
 */
public final class ConnectionNamePolicy {

  public static final int MAX_NAME_CODE_POINTS = 255;

  private static final String NAME_FIELD = "name";

  private ConnectionNamePolicy() {}

  /** Requires a name that says something and fits the display-name bound. */
  public static void require(String name) {
    if (isBlank(name)) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.BLANK_CONNECTION_NAME);
    }
    if (exceedsBound(name)) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.CONNECTION_NAME_TOO_LONG, NAME_FIELD, MAX_NAME_CODE_POINTS);
    }
  }

  private static boolean isBlank(String name) {
    return name == null || name.isBlank();
  }

  private static boolean exceedsBound(String name) {
    return name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS;
  }
}
