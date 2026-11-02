// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.create;

import com.opencop.core.sdk.model.connection.ConnectionInfo;
import java.util.Objects;

/**
 * The connection a creation made, as its owner now sees it.
 *
 * @param connection the stored connection, runtime state included
 * @param receipt what creating it issued about it, which the owner receives only here
 */
public record ConnectionCreationResult(ConnectionInfo connection, CreationReceipt receipt) {

  public ConnectionCreationResult {
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(receipt, "receipt");
  }
}
