// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.UUID;
import java.util.function.Function;

/**
 * Holds a connection's row lock while work runs, which every other change to the connection waits
 * on: replacing its setup, patching it, deleting it, and writing its extensions' records and
 * secrets.
 */
public interface ConnectionLock {

  /**
   * Locks the connection, reads it as committed, and runs the work in one unit: what the work wrote
   * is kept when it returns, and undone when it throws.
   *
   * @param work given the connection as read under the lock
   * @throws GlobalOpenCopException 404 when the connection does not exist
   */
  <T> T holding(UUID sourceId, Function<ConnectionMetadata, T> work);
}
