// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.repository;

/**
 * Persisting a data source violated the active-name uniqueness index — another non-archived data
 * source already holds the name.
 *
 * <p>Persistence-level: it reports which constraint the database rejected, and leaves the caller to
 * decide what that means for the request being served.
 */
public class DataSourceNameConflictException extends RuntimeException {

  private final String name;

  public DataSourceNameConflictException(String name, Throwable cause) {
    super("A data source named '" + name + "' already exists", cause);
    this.name = name;
  }

  public String name() {
    return name;
  }
}
