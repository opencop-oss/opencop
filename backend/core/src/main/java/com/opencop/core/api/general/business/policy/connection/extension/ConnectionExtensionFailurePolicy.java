// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.extension;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;

/** How a failure an extension reports reaches the caller. */
public final class ConnectionExtensionFailurePolicy {

  private ConnectionExtensionFailurePolicy() {}

  /**
   * The error answering the caller: the failure family decides the status, and the extension's own
   * code, when it gave one, replaces the family's default code. The extension's message is meant
   * for the caller, so it is kept.
   */
  public static ConnectionManagementException toError(ConnectionExtensionException failure) {
    var family = familyOf(failure);
    return new ConnectionManagementException(
        failure.code().orElse(family.code()), failure.getMessage(), family.httpStatus());
  }

  /**
   * Whether the failure lies beyond OpenCOP, in the remote system the extension reached, rather
   * than in what the caller asked for.
   */
  public static boolean isUpstream(ConnectionExtensionException failure) {
    return switch (failure.failure()) {
      case UPSTREAM_FAILURE, UPSTREAM_TIMEOUT -> true;
      case BAD_REQUEST, NOT_FOUND, CONFLICT -> false;
    };
  }

  private static ConnectionExceptionCodes familyOf(ConnectionExtensionException failure) {
    return switch (failure.failure()) {
      case BAD_REQUEST -> ConnectionExceptionCodes.EXTENSION_BAD_REQUEST;
      case NOT_FOUND -> ConnectionExceptionCodes.EXTENSION_NOT_FOUND;
      case CONFLICT -> ConnectionExceptionCodes.EXTENSION_CONFLICT;
      case UPSTREAM_FAILURE -> ConnectionExceptionCodes.EXTENSION_UPSTREAM_FAILURE;
      case UPSTREAM_TIMEOUT -> ConnectionExceptionCodes.EXTENSION_UPSTREAM_TIMEOUT;
    };
  }
}
