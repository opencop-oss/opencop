// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.repository;

/**
 * Persisting a data source violated the active listen-port uniqueness index — another non-archived
 * data source of the same transport already binds the port.
 *
 * <p>Persistence-level: it reports which constraint the database rejected, and leaves the caller to
 * decide what that means for the request being served.
 */
public class DataSourceListenPortConflictException extends RuntimeException {

  private final int listenPort;

  public DataSourceListenPortConflictException(int listenPort, Throwable cause) {
    super("Listen port " + listenPort + " is already in use", cause);
    this.listenPort = listenPort;
  }

  public int listenPort() {
    return listenPort;
  }
}
