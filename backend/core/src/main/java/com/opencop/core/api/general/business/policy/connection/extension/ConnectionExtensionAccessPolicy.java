// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.extension;

import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;

/**
 * Who may invoke an extension operation, beyond seeing its connection. Listing and invoking
 * operations both decide through it, so a client is never offered what would be refused.
 */
public final class ConnectionExtensionAccessPolicy {

  private ConnectionExtensionAccessPolicy() {}

  /** Whether invoking an operation of this level takes administering its connection. */
  public static boolean requiresAdministration(AccessLevel access) {
    return switch (access) {
      case READ -> false;
      case MANAGE -> true;
    };
  }

  /** Whether a caller who can see the connection may invoke an operation of this level. */
  public static boolean isInvocable(AccessLevel access, boolean administersConnection) {
    return administersConnection || !requiresAdministration(access);
  }
}
