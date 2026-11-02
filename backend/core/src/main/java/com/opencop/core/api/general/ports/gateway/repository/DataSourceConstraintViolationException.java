// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.repository;

/**
 * Persisting a data source was rejected by the database, and the violation could not be attributed
 * to a constraint the repository knows how to report — the driver named a constraint that is not
 * modelled here, or supplied no constraint at all.
 *
 * <p>Persistence-level: it reports that nothing was written, and leaves the caller to decide what
 * that means for the request being served. Callers that provisioned external resources before
 * persisting must still release them; the row does not exist to release them later.
 */
public class DataSourceConstraintViolationException extends RuntimeException {

  private final String name;

  public DataSourceConstraintViolationException(String name, Throwable cause) {
    super("Persisting data source '" + name + "' violated a database constraint", cause);
    this.name = name;
  }

  public String name() {
    return name;
  }
}
